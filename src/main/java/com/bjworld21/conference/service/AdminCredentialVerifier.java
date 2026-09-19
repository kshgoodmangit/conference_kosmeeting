package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.config.AdminRolePolicy;
import com.bjworld21.conference.entity.AdminAccount;
import com.bjworld21.conference.repository.AdminAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCredentialVerifier {
    private static final String AUTHENTICATION_FAILED_MESSAGE = "관리자 인증에 실패했습니다.";
    private static final String LOCKED_MESSAGE = "비밀번호 5회 실패로 계정이 잠겼습니다. 다른 관리자에게 비밀번호 초기화를 요청해 주세요.";
    private static final int MAX_FAILURES = 5;

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final PersonalDataProperties personalDataProperties;

    public AdminCredentialVerifier(
            AdminAccountRepository adminAccountRepository,
            PasswordEncoder passwordEncoder,
            PersonalDataProperties personalDataProperties
    ) {
        this.adminAccountRepository = adminAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.personalDataProperties = personalDataProperties;
    }

    // 인증 거부 시에도 실패 기록은 커밋하고, 후속 업무의 롤백과 분리합니다.
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = AuthenticationRejectedException.class)
    public AdminAccount verifyLogin(String email, String password) {
        return verify(email, password, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = AuthenticationRejectedException.class)
    public AdminAccount verifyActiveAdministrator(String email, String password) {
        return verify(email, password, true);
    }

    private AdminAccount verify(String email, String password, boolean administratorsOnly) {
        if (email == null || email.isBlank() || password == null || password.isEmpty()) {
            throw new AuthenticationRejectedException(AUTHENTICATION_FAILED_MESSAGE);
        }

        // 동일 계정의 비밀번호 검증과 실패 기록을 하나의 행 잠금 안에서 처리합니다.
        AdminAccount admin = adminAccountRepository.findByEmailForUpdate(
                email.trim(), personalDataProperties.requireDbEncString()
        );
        if (admin == null
                || !"active".equals(admin.getStatus())
                || (administratorsOnly && !AdminRolePolicy.isFullAdministrator(admin.getRole()))) {
            throw new AuthenticationRejectedException(AUTHENTICATION_FAILED_MESSAGE);
        }

        int failures = admin.getLoginFailureCount();
        if (failures >= MAX_FAILURES || admin.getLoginLockedAt() != null) {
            throw new AuthenticationRejectedException(LOCKED_MESSAGE);
        }

        if (!passwordEncoder.matches(password, admin.getPassword())) {
            int nextFailures = Math.min(failures + 1, MAX_FAILURES);
            adminAccountRepository.recordLoginFailure(admin.getSeq(), nextFailures);
            if (nextFailures >= MAX_FAILURES) {
                throw new AuthenticationRejectedException(LOCKED_MESSAGE);
            }
            if (nextFailures >= 3) {
                throw new AuthenticationRejectedException(
                        "비밀번호가 일치하지 않습니다. " + (MAX_FAILURES - nextFailures) + "회 더 실패하면 계정이 잠깁니다."
                );
            }
            throw new AuthenticationRejectedException(AUTHENTICATION_FAILED_MESSAGE);
        }

        adminAccountRepository.clearLoginFailures(admin.getSeq());
        return admin;
    }

    public static class AuthenticationRejectedException extends IllegalArgumentException {
        public AuthenticationRejectedException(String message) {
            super(message);
        }
    }
}
