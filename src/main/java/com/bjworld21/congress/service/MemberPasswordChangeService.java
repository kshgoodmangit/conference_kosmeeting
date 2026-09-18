package com.bjworld21.congress.service;

import com.bjworld21.congress.repository.MemberPasswordResetRepository;
import com.bjworld21.congress.security.MemberCredentialFingerprint;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Service
public class MemberPasswordChangeService {
    private final MemberPasswordResetRepository credentials;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transactions;
    private final TransactionTemplate limits;
    private final Clock clock;

    public MemberPasswordChangeService(MemberPasswordResetRepository credentials, PasswordEncoder encoder,
                                       PlatformTransactionManager manager, Clock clock) {
        this.credentials = credentials;
        this.encoder = encoder;
        this.transactions = new TransactionTemplate(manager);
        this.limits = new TransactionTemplate(manager);
        this.limits.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public void change(long conferenceSeq, long memberSeq, String fingerprint,
                       String currentPassword, String newPassword, String confirmation) {
        if (fingerprint == null) throw new SessionExpiredException();
        if (currentPassword == null || currentPassword.isBlank() || currentPassword.length() > 256) {
            throw new InvalidPasswordException("currentPassword", "Enter your current password.");
        }
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 16
                || !newPassword.equals(newPassword.trim())) {
            throw new InvalidPasswordException("newPassword", "Use 8-16 characters without leading or trailing spaces.");
        }
        if (!newPassword.equals(confirmation)) {
            throw new InvalidPasswordException("newPasswordConfirm", "Passwords do not match.");
        }

        // Commit attempts independently so incorrect passwords cannot roll back the limit.
        LocalDateTime window = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS);
        String key = MemberCredentialFingerprint.hash("password-change:" + conferenceSeq + ":" + memberSeq);
        boolean allowed = Boolean.TRUE.equals(limits.execute(status -> {
            credentials.ensureLimit(key, window);
            return credentials.takeLimit(key, window, 5) == 1;
        }));
        if (!allowed) throw new TooManyAttemptsException();

        transactions.executeWithoutResult(status -> {
            // Use the same member lock as password recovery to serialize concurrent changes.
            String current = credentials.lockCredential(conferenceSeq, memberSeq);
            if (current == null || !MemberCredentialFingerprint.hash(current).equals(fingerprint)) {
                throw new SessionExpiredException();
            }
            if (!encoder.matches(currentPassword.trim(), current)) {
                throw new InvalidPasswordException("currentPassword", "Your current password is incorrect.");
            }
            if (encoder.matches(newPassword, current)) {
                throw new InvalidPasswordException("newPassword", "Choose a password different from your current password.");
            }
            if (credentials.updatePassword(conferenceSeq, memberSeq, encoder.encode(newPassword)) != 1) {
                throw new SessionExpiredException();
            }
            credentials.consumeAll(conferenceSeq, memberSeq, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        });
    }

    public static class InvalidPasswordException extends RuntimeException {
        private final String field;
        public InvalidPasswordException(String field, String message) {
            super(message);
            this.field = field;
        }
        public String getField() { return field; }
    }
    public static class SessionExpiredException extends RuntimeException {}
    public static class TooManyAttemptsException extends RuntimeException {}
}
