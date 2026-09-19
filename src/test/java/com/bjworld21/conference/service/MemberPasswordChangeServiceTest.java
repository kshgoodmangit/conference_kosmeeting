package com.bjworld21.conference.service;

import com.bjworld21.conference.repository.MemberPasswordResetRepository;
import com.bjworld21.conference.security.MemberCredentialFingerprint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemberPasswordChangeServiceTest {
    private final MemberPasswordResetRepository repository = mock(MemberPasswordResetRepository.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final String hash = encoder.encode("OldPassword123!");
    private final String fingerprint = MemberCredentialFingerprint.hash(hash);
    private MemberPasswordChangeService service;

    @BeforeEach
    void setUp() {
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(repository.takeLimit(anyString(), any(), eq(5))).thenReturn(1);
        when(repository.lockCredential(7L, 11L)).thenReturn(hash);
        when(repository.updatePassword(eq(7L), eq(11L), anyString())).thenReturn(1);
        service = new MemberPasswordChangeService(repository, encoder, manager,
                Clock.fixed(Instant.parse("2026-09-16T01:30:00Z"), ZoneOffset.UTC));
    }

    @Test
    void changesOnlyOwnedCredentialAndConsumesRecoveryLinks() {
        service.change(7L, 11L, fingerprint, "OldPassword123!", "NewPassword123!", "NewPassword123!");
        var password = ArgumentCaptor.forClass(String.class);
        verify(repository).updatePassword(eq(7L), eq(11L), password.capture());
        assertThat(encoder.matches("NewPassword123!", password.getValue())).isTrue();
        assertThat(encoder.matches("OldPassword123!", password.getValue())).isFalse();
        verify(repository).consumeAll(7L, 11L, LocalDateTime.of(2026, 9, 16, 1, 30));
        var order = inOrder(repository, manager);
        order.verify(repository).takeLimit(anyString(), any(), eq(5));
        order.verify(manager).commit(any());
        order.verify(repository).lockCredential(7L, 11L);
        order.verify(repository).updatePassword(eq(7L), eq(11L), anyString());
        order.verify(repository).consumeAll(eq(7L), eq(11L), any());
        order.verify(manager).commit(any());
    }

    @Test
    void incorrectCurrentPasswordCommitsAttemptButRollsBackChange() {
        assertThatThrownBy(() -> service.change(7L, 11L, fingerprint, "wrong", "NewPassword123!", "NewPassword123!"))
                .isInstanceOf(MemberPasswordChangeService.InvalidPasswordException.class)
                .hasMessageContaining("current password is incorrect");
        verify(manager).commit(any());
        verify(manager).rollback(any());
        verify(repository, never()).updatePassword(anyLong(), anyLong(), anyString());
    }

    @Test
    void staleSessionCannotOverwriteAnotherPasswordChange() {
        when(repository.lockCredential(7L, 11L)).thenReturn(encoder.encode("AnotherPassword"));
        assertThatThrownBy(() -> service.change(7L, 11L, fingerprint, "OldPassword123!", "NewPassword123!", "NewPassword123!"))
                .isInstanceOf(MemberPasswordChangeService.SessionExpiredException.class);
        verify(repository, never()).updatePassword(anyLong(), anyLong(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "12345678901234567", " Password123", "Password123 "})
    void rejectsInvalidNewPasswords(String password) {
        assertThatThrownBy(() -> service.change(7L, 11L, fingerprint, "OldPassword123!", password, password))
                .isInstanceOf(MemberPasswordChangeService.InvalidPasswordException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMismatchAndUnchangedPassword() {
        assertThatThrownBy(() -> service.change(7L, 11L, fingerprint, "OldPassword123!", "NewPassword123!", "OtherPassword"))
                .hasMessage("Passwords do not match.");
        assertThatThrownBy(() -> service.change(7L, 11L, fingerprint, "OldPassword123!", "OldPassword123!", "OldPassword123!"))
                .hasMessageContaining("different");
        verify(repository, never()).updatePassword(anyLong(), anyLong(), anyString());
    }

    @Test
    void accountLimitBlocksGuessingBeforePasswordComparison() {
        when(repository.takeLimit(anyString(), any(), eq(5))).thenReturn(0);
        assertThatThrownBy(() -> service.change(7L, 11L, fingerprint, "wrong", "NewPassword123!", "NewPassword123!"))
                .isInstanceOf(MemberPasswordChangeService.TooManyAttemptsException.class);
        verify(repository, never()).lockCredential(anyLong(), anyLong());
    }

    @Test
    void failureToConsumeTokensRollsBackPasswordWrite() {
        doThrow(new IllegalStateException("database failure")).when(repository).consumeAll(anyLong(), anyLong(), any());
        assertThatThrownBy(() -> service.change(7L, 11L, fingerprint, "OldPassword123!", "NewPassword123!", "NewPassword123!"))
                .isInstanceOf(IllegalStateException.class);
        verify(manager).rollback(any());
    }
}
