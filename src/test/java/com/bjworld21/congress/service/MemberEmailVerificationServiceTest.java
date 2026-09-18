package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.ConferenceSettingsResponse;
import com.bjworld21.congress.repository.MemberEmailVerificationRepository;
import com.bjworld21.congress.repository.MemberRepository;
import com.bjworld21.congress.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberEmailVerificationServiceTest {
    private final MemberEmailVerificationRepository repository = mock(MemberEmailVerificationRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final MemberEmailVerificationMailService mail = mock(MemberEmailVerificationMailService.class);
    private final ConferenceSettingsService conferences = mock(ConferenceSettingsService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final Instant now = Instant.parse("2026-09-16T00:00:00Z");
    private MemberEmailVerificationService service;

    @BeforeEach
    void setup() {
        var properties = new PersonalDataProperties();
        properties.setDbEncString("test-only-key");
        when(conferences.getLatestConferenceSeq()).thenReturn(7L);
        when(conferences.getSettings(7L)).thenReturn(ConferenceSettingsResponse.builder().eventName("APDRC8").build());
        when(repository.takeLimit(anyString(), any(), any(), anyInt())).thenReturn(1);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new MemberEmailVerificationService(repository, properties, mail, conferences, encoder, transactions,
                Clock.fixed(now, ZoneOffset.UTC), members);
    }

    @Test
    void deliversSixDigitsAndStoresOnlyHashWithEmailConferenceAndExpiryInSession() throws Exception {
        var session = new MockHttpSession();
        var result = service.sendCode(" Member@Example.com ", "192.0.2.1", session);
        var code = ArgumentCaptor.forClass(String.class);
        verify(mail).sendCode(eq("member@example.com"), eq("APDRC8"), code.capture(), eq(10L));
        assertThat(code.getValue()).matches("[0-9]{6}");
        var challenge = (MemberEmailVerificationService.Challenge) session.getAttribute(MemberEmailVerificationService.SESSION_KEY);
        assertThat(challenge.email()).isEqualTo("member@example.com");
        assertThat(challenge.conferenceSeq()).isEqualTo(7);
        assertThat(challenge.expiresAt()).isEqualTo(now.plusSeconds(600));
        assertThat(challenge.codeHash()).isNotEqualTo(code.getValue());
        assertThat(encoder.matches(code.getValue(), challenge.codeHash())).isTrue();
        assertThat(result.expiresInSeconds()).isEqualTo(600);
        assertThat(result.resendAfterSeconds()).isEqualTo(60);
        assertThat(result.toString()).doesNotContain(code.getValue(), "member@example.com");
        var hashes = ArgumentCaptor.forClass(String.class);
        verify(repository, times(3)).ensureLimit(hashes.capture(), any());
        assertThat(hashes.getAllValues()).allMatch(hash -> hash.matches("[a-f0-9]{64}"));
        verify(transactions).commit(any());
    }

    @Test
    void failedSmtpInvalidatesPreviousCodeAndDoesNotMarkNewCodeSent() throws Exception {
        var session = new MockHttpSession();
        session.setAttribute(MemberEmailVerificationService.SESSION_KEY, "old-challenge");
        doThrow(new IllegalStateException("smtp rejected")).when(mail).sendCode(anyString(), anyString(), anyString(), anyLong());
        assertThatThrownBy(() -> service.sendCode("member@example.com", "192.0.2.1", session))
                .isInstanceOf(IllegalStateException.class);
        assertThat(session.getAttribute(MemberEmailVerificationService.SESSION_KEY)).isNull();
        verify(transactions).commit(any());
    }

    @Test
    void exhaustedLimitPreventsSendingAndIssuance() throws Exception {
        when(repository.takeLimit(anyString(), any(), any(), anyInt())).thenReturn(0);
        var session = new MockHttpSession();
        assertThatThrownBy(() -> service.sendCode("member@example.com", "192.0.2.1", session))
                .isInstanceOf(MemberEmailVerificationService.TooManyRequestsException.class);
        verify(mail, never()).sendCode(anyString(), anyString(), anyString(), anyLong());
        assertThat(session.getAttribute(MemberEmailVerificationService.SESSION_KEY)).isNull();
        verify(transactions).commit(any());
    }

    @Test
    void invalidAddressCannotReachMailOrDatabase() {
        assertThatThrownBy(() -> service.sendCode("invalid\r\nBcc: other@example.com", "192.0.2.1", new MockHttpSession()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mail, repository);
    }

    @Test
    void resendingReplacesSessionChallenge() throws Exception {
        var session = new MockHttpSession();
        service.sendCode("first@example.com", "192.0.2.1", session);
        Object first = session.getAttribute(MemberEmailVerificationService.SESSION_KEY);
        service.sendCode("second@example.com", "192.0.2.1", session);
        var second = (MemberEmailVerificationService.Challenge) session.getAttribute(MemberEmailVerificationService.SESSION_KEY);
        assertThat(second).isNotSameAs(first);
        assertThat(second.email()).isEqualTo("second@example.com");
    }

    private MockHttpSession challenge(Instant expiry) {
        var session = new MockHttpSession();
        session.setAttribute(MemberEmailVerificationService.SESSION_KEY,
                new MemberEmailVerificationService.Challenge(7L, "member@example.com", encoder.encode("123456"), expiry));
        return session;
    }

    @Test
    void validCodeCreatesBoundSingleUseRegistrationProof() {
        var session = challenge(now.plusSeconds(600));
        var result = service.verifyCode(" MEMBER@example.com ", "123456", "192.0.2.1", session);
        assertThat(result.existingMember()).isFalse();
        assertThat(result.expiresInSeconds()).isEqualTo(1800);
        assertThat(session.getAttribute(MemberEmailVerificationService.SESSION_KEY)).isNull();
        assertThat(service.completeRegistration(7L, "MEMBER@example.com", session, () -> "created")).isEqualTo("created");
        assertThatThrownBy(() -> service.completeRegistration(7L, "member@example.com", session, () -> "duplicate"))
                .isInstanceOf(MemberEmailVerificationService.InvalidCodeException.class);
        assertThatThrownBy(() -> service.verifyCode("member@example.com", "123456", "192.0.2.1", session))
                .isInstanceOf(MemberEmailVerificationService.InvalidCodeException.class);
    }

    @Test
    void existingAccountIsDisclosedOnlyAfterCorrectCodeAndCannotRegister() {
        var session = challenge(now.plusSeconds(600));
        when(members.findByEmail(7L, "member@example.com", "test-only-key")).thenReturn(new Member());
        assertThatThrownBy(() -> service.verifyCode("member@example.com", "000000", "192.0.2.1", session))
                .isInstanceOf(MemberEmailVerificationService.IncorrectCodeException.class);
        verifyNoInteractions(members);
        assertThat(service.verifyCode("member@example.com", "123456", "192.0.2.1", session).existingMember()).isTrue();
        assertThat(session.getAttribute(MemberEmailVerificationService.VERIFIED_KEY)).isNull();
        assertThatThrownBy(() -> service.completeRegistration(7L, "member@example.com", session, () -> "created"))
                .isInstanceOf(MemberEmailVerificationService.InvalidCodeException.class);
    }

    @Test
    void fiveWrongCodesInvalidateChallengeEvenIfCorrectCodeIsSubmittedNext() {
        var session = challenge(now.plusSeconds(600));
        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThatThrownBy(() -> service.verifyCode("member@example.com", "000000", "192.0.2.1", session))
                    .isInstanceOf(MemberEmailVerificationService.IncorrectCodeException.class)
                    .hasMessageContaining((5 - attempt) + " attempts remaining");
        }
        assertThatThrownBy(() -> service.verifyCode("member@example.com", "000000", "192.0.2.1", session))
                .hasMessageContaining("Too many incorrect codes");
        assertThatThrownBy(() -> service.verifyCode("member@example.com", "123456", "192.0.2.1", session))
                .isInstanceOf(MemberEmailVerificationService.InvalidCodeException.class);
        verifyNoInteractions(members);
    }

    @Test
    void expiredOrUnboundCodesCannotVerify() {
        assertThatThrownBy(() -> service.verifyCode("member@example.com", "123456", "192.0.2.1", challenge(now)))
                .hasMessageContaining("expired");
        assertThatThrownBy(() -> service.verifyCode("other@example.com", "123456", "192.0.2.1", challenge(now.plusSeconds(600))))
                .isInstanceOf(MemberEmailVerificationService.InvalidCodeException.class);
        assertThatThrownBy(() -> service.verifyCode("member@example.com", "123456", "192.0.2.1", null))
                .isInstanceOf(MemberEmailVerificationService.InvalidCodeException.class);
        when(conferences.getLatestConferenceSeq()).thenReturn(8L);
        assertThatThrownBy(() -> service.verifyCode("member@example.com", "123456", "192.0.2.1", challenge(now.plusSeconds(600))))
                .isInstanceOf(MemberEmailVerificationService.InvalidCodeException.class);
        verifyNoInteractions(members);
    }

    @Test
    void registrationRejectsWrongEmailConferenceExpiredOrMissingProofWithoutCallingCreation() {
        var session = challenge(now.plusSeconds(600));
        service.verifyCode("member@example.com", "123456", "192.0.2.1", session);
        java.util.function.Supplier<String> create = () -> { throw new AssertionError("Must not register"); };
        assertThatThrownBy(() -> service.completeRegistration(7L, "other@example.com", session, create))
                .isInstanceOf(MemberEmailVerificationService.InvalidCodeException.class);
        assertThatThrownBy(() -> service.completeRegistration(8L, "member@example.com", session, create))
                .isInstanceOf(MemberEmailVerificationService.InvalidCodeException.class);
        assertThatThrownBy(() -> service.completeRegistration(7L, "member@example.com", null, create))
                .isInstanceOf(MemberEmailVerificationService.InvalidCodeException.class);
        session.setAttribute(MemberEmailVerificationService.VERIFIED_KEY,
                new MemberEmailVerificationService.VerifiedEmail(7L, "member@example.com", now));
        assertThatThrownBy(() -> service.completeRegistration(7L, "member@example.com", session, create))
                .isInstanceOf(MemberEmailVerificationService.InvalidCodeException.class);
    }

    @Test
    void failedRegistrationPreservesProofButResendingRemovesIt() throws Exception {
        var session = challenge(now.plusSeconds(600));
        service.verifyCode("member@example.com", "123456", "192.0.2.1", session);
        assertThatThrownBy(() -> service.completeRegistration(7L, "member@example.com", session,
                () -> { throw new IllegalArgumentException("Invalid name"); })).hasMessage("Invalid name");
        assertThat(session.getAttribute(MemberEmailVerificationService.VERIFIED_KEY)).isNotNull();
        service.sendCode("member@example.com", "192.0.2.1", session);
        assertThat(session.getAttribute(MemberEmailVerificationService.VERIFIED_KEY)).isNull();
    }

    @Test
    void verificationIpLimitPreventsMembershipLookup() {
        when(repository.takeLimit(anyString(), any(), any(), anyInt())).thenReturn(0);
        assertThatThrownBy(() -> service.verifyCode("member@example.com", "123456", "192.0.2.1", challenge(now.plusSeconds(600))))
                .isInstanceOf(MemberEmailVerificationService.VerificationRateLimitException.class);
        verifyNoInteractions(members);
    }
}
