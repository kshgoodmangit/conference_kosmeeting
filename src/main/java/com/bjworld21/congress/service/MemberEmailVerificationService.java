package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.repository.MemberEmailVerificationRepository;
import com.bjworld21.congress.repository.MemberRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.WebUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class MemberEmailVerificationService {
    public static final String SESSION_KEY = MemberEmailVerificationService.class.getName() + ".challenge";
    public static final String VERIFIED_KEY = MemberEmailVerificationService.class.getName() + ".verified";
    private static final String ATTEMPTS_KEY = SESSION_KEY + ".attempts";
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final SecureRandom RANDOM = new SecureRandom();
    private final MemberEmailVerificationRepository repository;
    private final PersonalDataProperties personalData;
    private final MemberEmailVerificationMailService mail;
    private final ConferenceSettingsService conferences;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final MemberRepository members;

    public MemberEmailVerificationService(MemberEmailVerificationRepository repository,
            PersonalDataProperties personalData, MemberEmailVerificationMailService mail,
            ConferenceSettingsService conferences, PasswordEncoder encoder,
            PlatformTransactionManager transactionManager, Clock clock, MemberRepository members) {
        this.repository = repository;
        this.personalData = personalData;
        this.mail = mail;
        this.conferences = conferences;
        this.encoder = encoder;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.members = members;
    }

    public SendResult sendCode(String address, String ip, HttpSession session) throws Exception {
        String email = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 255 || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email address");
        }
        mail.checkAvailable();
        long conferenceSeq = conferences.getLatestConferenceSeq();
        // SMTP is outside the DB transaction; only the current browser's issuance is serialized.
        synchronized (WebUtils.getSessionMutex(session)) {
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            boolean allowed = Boolean.TRUE.equals(transactions.execute(status ->
                    takeLimit("send-ip:" + ip, now, 3600, 30)
                    && takeLimit("send-email:" + conferenceSeq + ":" + email, now, 3600, 5)
                    && takeLimit("send-minute:" + conferenceSeq + ":" + email, now, 60, 1)));
            if (!allowed) throw new TooManyRequestsException();

            session.removeAttribute(SESSION_KEY);
            session.removeAttribute(VERIFIED_KEY);
            session.removeAttribute(ATTEMPTS_KEY);
            String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
            String hash = encoder.encode(code);
            // Do not check membership here: existing and new addresses receive the same response.
            mail.sendCode(email, conferences.getSettings(conferenceSeq).getEventName(), code, 10);
            Instant expiresAt = clock.instant().plusSeconds(600);
            session.setAttribute(SESSION_KEY, new Challenge(conferenceSeq, email, hash, expiresAt));
            return new SendResult("A verification code has been sent. Please check your inbox and spam folder.", 600, 60);
        }
    }

    public VerifyResult verifyCode(String address, String code, String ip, HttpSession session) {
        if (session == null) throw new InvalidCodeException("Please request a new verification code.");
        String email = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
        long conferenceSeq = conferences.getLatestConferenceSeq();
        synchronized (WebUtils.getSessionMutex(session)) {
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            boolean allowed = Boolean.TRUE.equals(transactions.execute(status -> takeLimit("verify-ip:" + ip, now, 3600, 60)));
            if (!allowed) throw new VerificationRateLimitException();
            if (!(session.getAttribute(SESSION_KEY) instanceof Challenge challenge)
                    || challenge.conferenceSeq() != conferenceSeq || !challenge.email().equals(email)) {
                throw new InvalidCodeException("Please request a new verification code for this email address.");
            }
            if (!challenge.expiresAt().isAfter(clock.instant())) {
                session.removeAttribute(SESSION_KEY);
                throw new InvalidCodeException("The code has expired. Please request a new code.");
            }
            int attempts = session.getAttribute(ATTEMPTS_KEY) instanceof Integer count ? count : 0;
            if (code == null || !code.matches("[0-9]{6}") || !encoder.matches(code, challenge.codeHash())) {
                attempts++;
                session.setAttribute(ATTEMPTS_KEY, attempts);
                if (attempts >= 5) {
                    session.removeAttribute(SESSION_KEY);
                    throw new InvalidCodeException("Too many incorrect codes. Please request a new code.");
                }
                throw new IncorrectCodeException("The code is incorrect. " + (5 - attempts) + " attempts remaining.");
            }
            // Account existence is revealed only after proving possession of the emailed code.
            boolean existing = members.findByEmail(conferenceSeq, email, personalData.requireDbEncString()) != null;
            session.removeAttribute(SESSION_KEY);
            session.removeAttribute(ATTEMPTS_KEY);
            session.removeAttribute(VERIFIED_KEY);
            if (!existing) session.setAttribute(VERIFIED_KEY, new VerifiedEmail(conferenceSeq, email, clock.instant().plusSeconds(1800)));
            return new VerifyResult(existing
                    ? "This email address is already registered. Please log in or reset your password."
                    : "Email verified. Please complete your registration within 30 minutes.", existing, existing ? 0 : 1800);
        }
    }

    public <T> T completeRegistration(long conferenceSeq, String address, HttpSession session, java.util.function.Supplier<T> create) {
        if (session == null) throw new InvalidCodeException("Please verify your email before signing up.");
        synchronized (WebUtils.getSessionMutex(session)) {
            if (!(session.getAttribute(VERIFIED_KEY) instanceof VerifiedEmail proof)
                    || proof.conferenceSeq() != conferenceSeq
                    || !proof.email().equals(address.trim().toLowerCase(Locale.ROOT))
                    || !proof.expiresAt().isAfter(clock.instant())) {
                throw new InvalidCodeException("Please verify your email before signing up.");
            }
            // Keep proof for retryable form errors; consume it only after successful registration.
            T result = create.get();
            session.removeAttribute(VERIFIED_KEY);
            return result;
        }
    }

    private boolean takeLimit(String key, LocalDateTime now, int seconds, int maximum) {
        String hash;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(personalData.requireDbEncString().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            hash = HexFormat.of().formatHex(mac.doFinal(key.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Request limiting is unavailable", exception);
        }
        repository.ensureLimit(hash, now);
        return repository.takeLimit(hash, now, now.minusSeconds(seconds), maximum) == 1;
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
    public void purgeExpiredLimits() {
        try {
            repository.purgeLimits(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(1));
        } catch (Exception exception) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Email verification limit cleanup unavailable; check migration");
        }
    }

    public record Challenge(long conferenceSeq, String email, String codeHash, Instant expiresAt) implements Serializable {}
    public record VerifiedEmail(long conferenceSeq, String email, Instant expiresAt) implements Serializable {}
    public record VerifyResult(String message, boolean existingMember, int expiresInSeconds) {}
    public record SendResult(String message, int expiresInSeconds, int resendAfterSeconds) {}
    public static class TooManyRequestsException extends RuntimeException {}
    public static class VerificationRateLimitException extends RuntimeException {}
    public static class InvalidCodeException extends IllegalArgumentException {
        public InvalidCodeException(String message) { super(message); }
    }
    public static class IncorrectCodeException extends InvalidCodeException {
        public IncorrectCodeException(String message) { super(message); }
    }
}
