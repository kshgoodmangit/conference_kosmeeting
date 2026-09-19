package com.bjworld21.conference.service;

import com.bjworld21.conference.config.MemberPasswordResetProperties;
import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.dto.ConferenceSettingsResponse;
import com.bjworld21.conference.entity.Member;
import com.bjworld21.conference.entity.MemberPasswordResetToken;
import com.bjworld21.conference.repository.MemberPasswordResetRepository;
import com.bjworld21.conference.repository.MemberRepository;
import com.bjworld21.conference.security.MemberCredentialFingerprint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.*;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemberPasswordResetServiceTest {
    private static final String RAW_TOKEN = "a".repeat(43);
    private static final String HASH = MemberCredentialFingerprint.hash(RAW_TOKEN);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 15, 12, 0);
    private final MemberPasswordResetRepository tokens = mock(MemberPasswordResetRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final MemberPasswordResetMailService mail = mock(MemberPasswordResetMailService.class);
    private final ConferenceSettingsService conferences = mock(ConferenceSettingsService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final Executor executor = mock(Executor.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final MemberPasswordResetProperties properties = new MemberPasswordResetProperties();
    private MemberPasswordResetService service;

    @org.junit.jupiter.api.AfterEach
    void clearContext() { org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); }

    @BeforeEach
    void setUp() {
        var personalData = new PersonalDataProperties();
        personalData.setDbEncString("test-only-key");
        com.bjworld21.conference.publicsite.PublicSiteTestContext.bind(7L);
        when(conferences.getSettings(7L)).thenReturn(ConferenceSettingsResponse.builder().eventName("APDRC8").build());
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(tokens.takeLimit(anyString(), any(), anyInt())).thenReturn(1);
        service = new MemberPasswordResetService(tokens, members, properties, personalData, mail, conferences,
                encoder, transactionManager, executor, Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    @Test
    void queuesLookupAndSendsOnlyHashedExpiringTokensWithoutChangingPassword() throws Exception {
        when(tokens.lockMemberByEmail(7L, "member@example.com", "test-only-key"))
                .thenReturn(Member.builder().seq(11L).password("existing-bcrypt").build());
        service.requestReset(" Member@Example.com ", "192.0.2.1");
        verify(tokens, never()).lockMemberByEmail(anyLong(), anyString(), anyString());
        verify(mail, never()).sendResetLink(anyString(), anyString(), anyString(), anyLong(), anyString());
        // The request has ended before the asynchronous email task runs.
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        runQueuedTask();

        var stored = ArgumentCaptor.forClass(MemberPasswordResetToken.class);
        var link = ArgumentCaptor.forClass(String.class);
        verify(tokens).insert(stored.capture());
        verify(mail).sendResetLink(eq("member@example.com"), eq("APDRC8"), link.capture(), eq(30L), eq("en"));
        String raw = link.getValue().split("#token=")[1];
        assertThat(raw).matches("[A-Za-z0-9_-]{43}");
        assertThat(link.getValue()).startsWith("http://localhost:8080/apdrc8/en/reset-password#token=");
        assertThat(stored.getValue().getTokenHash()).isEqualTo(MemberCredentialFingerprint.hash(raw)).isNotEqualTo(raw);
        assertThat(stored.getValue().getExpiresAt()).isEqualTo(NOW.plusMinutes(30));
        assertThat(stored.getValue().getCredentialFingerprint()).isEqualTo(MemberCredentialFingerprint.hash("existing-bcrypt"));
        verify(tokens, never()).updatePassword(anyLong(), anyLong(), anyString());
        verify(tokens, never()).consumeAll(anyLong(), anyLong(), any());
    }

    @Test
    void unknownAddressStillQueuesLookupButDoesNotSendMail() throws Exception {
        service.requestReset("unknown@example.com", "192.0.2.1");
        runQueuedTask();
        verify(tokens, never()).insert(any());
        verify(mail, never()).sendResetLink(anyString(), anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void exhaustedLimitDoesNotEnqueueAndStoresNoRawEmailOrIp() {
        when(tokens.takeLimit(anyString(), any(), anyInt())).thenReturn(0);
        service.requestReset("member@example.com", "192.0.2.1");
        verifyNoInteractions(executor);
        var key = ArgumentCaptor.forClass(String.class);
        verify(tokens).ensureLimit(key.capture(), eq(NOW));
        assertThat(key.getValue()).matches("[0-9a-f]{64}").doesNotContain("member", "192.0.2.1");
    }

    @Test
    void smtpFailureDiscardsOnlyNewTokenAndNeverChangesPassword() throws Exception {
        when(tokens.lockMemberByEmail(anyLong(), anyString(), anyString()))
                .thenReturn(Member.builder().seq(11L).password("existing").build());
        doThrow(new jakarta.mail.MessagingException("simulated failure")).when(mail)
                .sendResetLink(anyString(), anyString(), anyString(), anyLong(), anyString());
        service.requestReset("member@example.com", "192.0.2.1");
        runQueuedTask();
        var stored = ArgumentCaptor.forClass(MemberPasswordResetToken.class);
        verify(tokens).insert(stored.capture());
        verify(tokens).deleteToken(stored.getValue().getTokenHash());
        verify(tokens, never()).updatePassword(anyLong(), anyLong(), anyString());
    }

    @Test
    void openingValidLinkDoesNotConsumeIt() {
        prepareToken();
        service.validateToken(RAW_TOKEN, "192.0.2.1");
        verify(tokens, never()).consumeAll(anyLong(), anyLong(), any());
        verify(tokens, never()).updatePassword(anyLong(), anyLong(), anyString());
    }

    @Test
    void resetStoresBcryptAndConsumesAllLinksInOneTransaction() {
        prepareToken();
        service.resetPassword(RAW_TOKEN, "NewPassword123!", "NewPassword123!", "192.0.2.1");
        var password = ArgumentCaptor.forClass(String.class);
        var order = inOrder(tokens);
        order.verify(tokens).lockCredential(7L, 11L);
        order.verify(tokens).lockToken(7L, HASH);
        order.verify(tokens).updatePassword(eq(7L), eq(11L), password.capture());
        order.verify(tokens).consumeAll(7L, 11L, NOW);
        assertThat(encoder.matches("NewPassword123!", password.getValue())).isTrue();
        assertThat(password.getValue()).isNotEqualTo("NewPassword123!");
        verify(transactionManager, times(2)).commit(any());
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void expiryBoundaryAndUsedTokensAreRejected() {
        var token = prepareToken();
        token.setExpiresAt(NOW);
        assertThatThrownBy(() -> service.validateToken(RAW_TOKEN, "192.0.2.1"))
                .isInstanceOf(MemberPasswordResetService.InvalidTokenException.class);
        token.setExpiresAt(NOW.plusMinutes(30));
        token.setUsedAt(NOW.minusMinutes(1));
        assertThatThrownBy(() -> service.resetPassword(RAW_TOKEN, "Password123!", "Password123!", "192.0.2.1"))
                .isInstanceOf(MemberPasswordResetService.InvalidTokenException.class);
        verify(tokens, never()).updatePassword(anyLong(), anyLong(), anyString());
    }

    @Test
    void passwordChangeAfterIssuanceRevokesLink() {
        prepareToken();
        when(tokens.lockCredential(7L, 11L)).thenReturn("a-new-credential");
        assertThatThrownBy(() -> service.resetPassword(RAW_TOKEN, "Password123!", "Password123!", "192.0.2.1"))
                .isInstanceOf(MemberPasswordResetService.InvalidTokenException.class);
        verify(transactionManager).rollback(any());
        verify(tokens, never()).updatePassword(anyLong(), anyLong(), anyString());
    }

    @Test
    void tokenConsumedByAnotherRequestIsRecheckedUnderLock() {
        prepareToken();
        var consumed = new MemberPasswordResetToken();
        consumed.setUsedAt(NOW);
        consumed.setExpiresAt(NOW.plusMinutes(30));
        when(tokens.lockToken(7L, HASH)).thenReturn(consumed);
        assertThatThrownBy(() -> service.resetPassword(RAW_TOKEN, "Password123!", "Password123!", "192.0.2.1"))
                .isInstanceOf(MemberPasswordResetService.InvalidTokenException.class);
        verify(tokens, never()).updatePassword(anyLong(), anyLong(), anyString());
        verify(transactionManager).rollback(any());
    }

    @Test
    void tokenConsumptionFailureRollsBackPasswordUpdate() {
        prepareToken();
        doThrow(new IllegalStateException("database error")).when(tokens).consumeAll(anyLong(), anyLong(), any());
        assertThatThrownBy(() -> service.resetPassword(RAW_TOKEN, "Password123!", "Password123!", "192.0.2.1"))
                .isInstanceOf(IllegalStateException.class);
        verify(transactionManager).rollback(any());
        verify(transactionManager, times(1)).commit(any()); // Only the separate rate-limit transaction committed.
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {8, 16})
    void resetAcceptsBothPasswordLengthBoundaries(int length) {
        prepareToken();
        String password = "a".repeat(length);
        service.resetPassword(RAW_TOKEN, password, password, "192.0.2.1");
        var stored = ArgumentCaptor.forClass(String.class);
        verify(tokens).updatePassword(eq(7L), eq(11L), stored.capture());
        assertThat(encoder.matches(password, stored.getValue())).isTrue();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {7, 17})
    void resetRejectsPasswordsOutsideTheLengthRange(int length) {
        String password = "a".repeat(length);
        assertThatThrownBy(() -> service.resetPassword(RAW_TOKEN, password, password, "192.0.2.1"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tokens, never()).updatePassword(anyLong(), anyLong(), anyString());
    }

    @Test
    void mismatchedWhitespaceAndOverlongMultibytePasswordsCannotBeStored() {
        assertThatThrownBy(() -> service.resetPassword(RAW_TOKEN, "Password123!", "OtherPassword", "192.0.2.1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.resetPassword(RAW_TOKEN, " Password123!", " Password123!", "192.0.2.1"))
                .isInstanceOf(IllegalArgumentException.class);
        String multibyte = "\uac00".repeat(25);
        assertThatThrownBy(() -> service.resetPassword(RAW_TOKEN, multibyte, multibyte, "192.0.2.1"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tokens, never()).updatePassword(anyLong(), anyLong(), anyString());
    }

    @Test
    void verificationRateLimitBlocksTokenLookup() {
        when(tokens.takeLimit(anyString(), any(), anyInt())).thenReturn(0);
        assertThatThrownBy(() -> service.validateToken(RAW_TOKEN, "192.0.2.1"))
                .isInstanceOf(MemberPasswordResetService.TooManyAttemptsException.class);
        verify(tokens, never()).find(anyLong(), anyString());
    }

    @Test
    void currentConferenceAlwaysScopesTokenLookup() {
        com.bjworld21.conference.publicsite.PublicSiteTestContext.bind(8L);
        assertThatThrownBy(() -> service.validateToken(RAW_TOKEN, "192.0.2.1"))
                .isInstanceOf(MemberPasswordResetService.InvalidTokenException.class);
        verify(tokens).find(8L, HASH);
        verifyNoInteractions(members);
    }

    @Test
    void unavailableConfigurationDoesNotEnqueueRequests() {
        properties.setPublicBaseUrl("http://example.com");
        assertThatThrownBy(() -> service.requestReset("member@example.com", "192.0.2.1"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(executor, tokens);
    }

    private MemberPasswordResetToken prepareToken() {
        var token = new MemberPasswordResetToken();
        token.setTokenHash(HASH);
        token.setConferenceSeq(7L);
        token.setMemberSeq(11L);
        token.setExpiresAt(NOW.plusMinutes(30));
        token.setCredentialFingerprint(MemberCredentialFingerprint.hash("existing"));
        when(tokens.find(7L, HASH)).thenReturn(token);
        when(tokens.lockToken(7L, HASH)).thenReturn(token);
        when(tokens.lockCredential(7L, 11L)).thenReturn("existing");
        when(members.findCredential(7L, 11L)).thenReturn("existing");
        when(tokens.updatePassword(eq(7L), eq(11L), anyString())).thenReturn(1);
        return token;
    }

    private void runQueuedTask() {
        var task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(task.capture());
        task.getValue().run();
    }
}
