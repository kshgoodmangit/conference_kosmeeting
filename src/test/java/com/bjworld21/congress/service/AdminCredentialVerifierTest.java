package com.bjworld21.congress.service;

import com.bjworld21.congress.entity.AdminAccount;
import com.bjworld21.congress.repository.AdminAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AdminCredentialVerifierTest {
    private AdminAccountRepository repository;
    private PasswordEncoder passwordEncoder;
    private AdminCredentialVerifier verifier;

    @BeforeEach
    void setUp() {
        repository = mock(AdminAccountRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        verifier = new AdminCredentialVerifier(
                repository, passwordEncoder, PersonalDataTestSupport.properties()
        );
    }

    @Test
    void acceptsActiveAdministratorWithMatchingPassword() {
        AdminAccount admin = account("admin", "active", "encoded-password");
        when(repository.findByEmailForUpdate("admin-id", PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(admin);
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);

        assertThatCode(() -> verifier.verifyActiveAdministrator(" admin-id ", "plain-password"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsActiveMaintenanceAccountWithMatchingPassword() {
        AdminAccount maintenance = account("maintenance", "active", "encoded-password");
        when(repository.findByEmailForUpdate("maintenance-id", PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(maintenance);
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);

        assertThatCode(() -> verifier.verifyActiveAdministrator("maintenance-id", "plain-password"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsReviewerEvenWhenPasswordMatches() {
        AdminAccount reviewer = account("reviewer", "active", "encoded-password");
        when(repository.findByEmailForUpdate("reviewer-id", PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(reviewer);

        assertThatThrownBy(() -> verifier.verifyActiveAdministrator("reviewer-id", "plain-password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("관리자 인증에 실패했습니다.");
    }

    @Test
    void rejectsIncorrectPassword() {
        AdminAccount admin = account("admin", "active", "encoded-password");
        when(repository.findByEmailForUpdate("admin-id", PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(admin);
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> verifier.verifyActiveAdministrator("admin-id", "wrong-password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("관리자 인증에 실패했습니다.");
    }

    @ParameterizedTest
    @CsvSource({
            "0, 관리자 인증에 실패했습니다.",
            "1, 관리자 인증에 실패했습니다.",
            "2, 비밀번호가 일치하지 않습니다. 2회 더 실패하면 계정이 잠깁니다.",
            "3, 비밀번호가 일치하지 않습니다. 1회 더 실패하면 계정이 잠깁니다."
    })
    void recordsFailuresBeforeThreshold(int previousFailures, String expectedMessage) {
        AdminAccount admin = account("admin", "active", "encoded-password");
        admin.setLoginFailureCount(previousFailures);
        when(repository.findByEmailForUpdate("admin-id", PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(admin);

        assertThatThrownBy(() -> verifier.verifyLogin("admin-id", "wrong-password"))
                .hasMessage(expectedMessage);
        verify(repository).recordLoginFailure(1L, previousFailures + 1);
        verify(repository, never()).clearLoginFailures(anyLong());
    }

    @Test
    void fifthFailureLocksAccountAndRecoverySharesTheCounter() {
        AdminAccount admin = account("admin", "active", "encoded-password");
        admin.setLoginFailureCount(4);
        when(repository.findByEmailForUpdate("admin-id", PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(admin);
        doAnswer(invocation -> {
            admin.setLoginFailureCount(invocation.getArgument(1));
            return null;
        }).when(repository).recordLoginFailure(anyLong(), anyInt());

        assertThatThrownBy(() -> verifier.verifyActiveAdministrator("admin-id", "wrong-password"))
                .hasMessageContaining("5회 실패로 계정이 잠겼습니다");
        verify(repository).recordLoginFailure(1L, 5);
        clearInvocations(passwordEncoder, repository);

        assertThatThrownBy(() -> verifier.verifyLogin("admin-id", "correct-password"))
                .hasMessageContaining("다른 관리자에게 비밀번호 초기화");
        verifyNoInteractions(passwordEncoder);
        verify(repository, never()).recordLoginFailure(anyLong(), anyInt());
        verify(repository, never()).clearLoginFailures(anyLong());
    }

    @Test
    void lockedAccountCannotAuthenticateThroughRecoveryEither() {
        AdminAccount admin = account("admin", "active", "encoded-password");
        admin.setLoginFailureCount(5);
        admin.setLoginLockedAt(java.time.LocalDateTime.of(2000, 1, 1, 0, 0));
        when(repository.findByEmailForUpdate("admin-id", PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(admin);

        assertThatThrownBy(() -> verifier.verifyActiveAdministrator("admin-id", "correct-password"))
                .hasMessageContaining("계정이 잠겼습니다");
        verifyNoInteractions(passwordEncoder);
        verify(repository, never()).clearLoginFailures(anyLong());
    }

    @Test
    void successfulAuthenticationBeforeLockResetsFailures() {
        AdminAccount admin = account("admin", "active", "encoded-password");
        admin.setLoginFailureCount(4);
        when(repository.findByEmailForUpdate("admin-id", PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(admin);
        when(passwordEncoder.matches("correct-password", "encoded-password")).thenReturn(true);

        assertThatCode(() -> verifier.verifyLogin(" admin-id ", "correct-password")).doesNotThrowAnyException();
        verify(repository).clearLoginFailures(1L);
        verify(repository, never()).recordLoginFailure(anyLong(), anyInt());
    }

    @Test
    void unknownAndInactiveAccountsDoNotUpdateFailureState() {
        assertThatThrownBy(() -> verifier.verifyLogin("missing", "password"))
                .hasMessage("관리자 인증에 실패했습니다.");
        when(repository.findByEmailForUpdate("inactive", PersonalDataTestSupport.DB_ENC_STRING))
                .thenReturn(account("admin", "inactive", "encoded-password"));
        assertThatThrownBy(() -> verifier.verifyLogin("inactive", "password"))
                .hasMessage("관리자 인증에 실패했습니다.");
        verifyNoInteractions(passwordEncoder);
        verify(repository, never()).recordLoginFailure(anyLong(), anyInt());
    }

    private AdminAccount account(String role, String status, String password) {
        return AdminAccount.builder()
                .seq(1L)
                .email("admin-id")
                .role(role)
                .status(status)
                .password(password)
                .build();
    }
}
