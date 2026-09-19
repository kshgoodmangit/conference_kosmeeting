package com.bjworld21.conference.service;

import com.bjworld21.conference.config.MemberPasswordResetProperties;
import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.publicsite.PublicApiRequest;
import com.bjworld21.conference.entity.MemberPasswordResetToken;
import com.bjworld21.conference.repository.MemberPasswordResetRepository;
import com.bjworld21.conference.repository.MemberRepository;
import com.bjworld21.conference.security.MemberCredentialFingerprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.Executor;

@Service
public class MemberPasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(MemberPasswordResetService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final MemberPasswordResetRepository tokens;
    private final MemberRepository members;
    private final MemberPasswordResetProperties properties;
    private final PersonalDataProperties personalData;
    private final MemberPasswordResetMailService mail;
    private final ConferenceSettingsService conferences;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transactions;
    private final Executor executor;
    private final Clock clock;

    public MemberPasswordResetService(MemberPasswordResetRepository tokens, MemberRepository members,
            MemberPasswordResetProperties properties, PersonalDataProperties personalData,
            MemberPasswordResetMailService mail, ConferenceSettingsService conferences, PasswordEncoder encoder,
            org.springframework.transaction.PlatformTransactionManager transactionManager,
            @Qualifier("memberPasswordResetExecutor") Executor executor, Clock clock) {
        this.tokens = tokens;
        this.members = members;
        this.properties = properties;
        this.personalData = personalData;
        this.mail = mail;
        this.conferences = conferences;
        this.encoder = encoder;
        this.transactions = new TransactionTemplate(transactionManager);
        this.executor = executor;
        this.clock = clock;
    }

    public void requestReset(String email, String ip) {
        checkEnabled();
        String baseUrl = properties.validatedBaseUrl();
        mail.checkAvailable();
        long conferenceSeq = PublicApiRequest.context().conferenceSeq();
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        LocalDateTime now = now();
        // Apply the same limits to every submitted address, including unknown accounts.
        boolean allowed = Boolean.TRUE.equals(transactions.execute(status ->
                takeLimit("request-ip:" + ip, now.truncatedTo(ChronoUnit.HOURS), 30)
                && takeLimit("request-email:" + conferenceSeq + ":" + normalizedEmail, now.truncatedTo(ChronoUnit.HOURS), 5)
                && takeLimit("request-minute:" + conferenceSeq + ":" + normalizedEmail, now.truncatedTo(ChronoUnit.MINUTES), 1)));
        if (!allowed) return;
        // Membership lookup and SMTP happen off the request thread, preventing account enumeration by timing.
        String resetPath = PublicApiRequest.context().pageUrl("/reset-password");
        String language = PublicApiRequest.context().language();
        executor.execute(() -> issueAndSend(conferenceSeq, normalizedEmail, baseUrl, resetPath, language));
    }

    private void issueAndSend(long conferenceSeq, String email, String baseUrl, String resetPath, String language) {
        String hash = null;
        try {
            byte[] random = new byte[32];
            RANDOM.nextBytes(random);
            String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            String tokenHash = MemberCredentialFingerprint.hash(rawToken);
            boolean created = Boolean.TRUE.equals(transactions.execute(status -> {
                var member = tokens.lockMemberByEmail(conferenceSeq, email, personalData.requireDbEncString());
                if (member == null || member.getPassword() == null) return false;
                var token = new MemberPasswordResetToken();
                token.setTokenHash(tokenHash);
                token.setConferenceSeq(conferenceSeq);
                token.setMemberSeq(member.getSeq());
                token.setCredentialFingerprint(MemberCredentialFingerprint.hash(member.getPassword()));
                token.setExpiresAt(now().plus(properties.getTokenTtl()));
                tokens.insert(token);
                return true;
            }));
            if (!created) return;
            hash = tokenHash;
            // Fragments are not sent in HTTP requests or Referer headers.
            String link = baseUrl + resetPath + "#token=" + rawToken;
            mail.sendResetLink(email, conferences.getSettings(conferenceSeq).getEventName(), link,
                    properties.getTokenTtl().toMinutes(), language);
        } catch (Exception exception) {
            if (hash != null) {
                try {
                    tokens.deleteToken(hash);
                } catch (Exception cleanupFailure) {
                    log.error("Could not discard a failed password reset delivery; conferenceSeq={}", conferenceSeq);
                }
            }
            // Never log addresses, credentials, token URLs, or SMTP exception messages.
            log.error("Password reset delivery failed; conferenceSeq={}, errorType={}",
                    conferenceSeq, exception.getClass().getSimpleName());
        }
    }

    public void validateToken(String rawToken, String ip) {
        checkEnabled();
        limitAttempt(ip);
        long conferenceSeq = PublicApiRequest.context().conferenceSeq();
        var token = tokens.find(conferenceSeq, MemberCredentialFingerprint.hash(rawToken));
        if (!usable(token, now())) throw new InvalidTokenException();
        String password = members.findCredential(conferenceSeq, token.getMemberSeq());
        if (password == null || !token.getCredentialFingerprint().equals(MemberCredentialFingerprint.hash(password))) {
            throw new InvalidTokenException();
        }
    }

    public void resetPassword(String rawToken, String password, String confirmation, String ip) {
        checkEnabled();
        limitAttempt(ip);
        if (password == null || password.length() < 8 || password.length() > 16
                || !password.equals(password.trim()) || !password.equals(confirmation)) {
            throw new IllegalArgumentException("Use matching passwords of 8-16 characters, without leading or trailing spaces.");
        }
        long conferenceSeq = PublicApiRequest.context().conferenceSeq();
        String hash = MemberCredentialFingerprint.hash(rawToken);
        var candidate = tokens.find(conferenceSeq, hash);
        if (!usable(candidate, now())) throw new InvalidTokenException();
        String encoded = encoder.encode(password);
        transactions.executeWithoutResult(status -> {
            // Lock the member before any token, consistently with issuance and other reset requests.
            String current = tokens.lockCredential(conferenceSeq, candidate.getMemberSeq());
            var token = tokens.lockToken(conferenceSeq, hash);
            LocalDateTime now = now();
            if (!usable(token, now) || current == null
                    || !token.getCredentialFingerprint().equals(MemberCredentialFingerprint.hash(current))) {
                throw new InvalidTokenException();
            }
            if (tokens.updatePassword(conferenceSeq, token.getMemberSeq(), encoded) != 1) {
                throw new InvalidTokenException();
            }
            tokens.consumeAll(conferenceSeq, token.getMemberSeq(), now);
        });
    }

    private void checkEnabled() {
        if (!properties.isEnabled()) throw new IllegalStateException("Password recovery is unavailable");
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
    public void purgeExpiredRecords() {
        if (!properties.isEnabled()) return;
        try {
            LocalDateTime cutoff = now().minusDays(1);
            tokens.purgeTokens(cutoff);
            tokens.purgeLimits(cutoff);
        } catch (Exception exception) {
            log.warn("Password recovery cleanup is unavailable; check that the migration has been applied");
        }
    }

    private void limitAttempt(String ip) {
        boolean allowed = Boolean.TRUE.equals(transactions.execute(status ->
                takeLimit("verify-ip:" + ip, now().truncatedTo(ChronoUnit.HOURS), 60)));
        if (!allowed) throw new TooManyAttemptsException();
    }

    private boolean takeLimit(String key, LocalDateTime window, int maximum) {
        String hash;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(personalData.requireDbEncString().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            hash = HexFormat.of().formatHex(mac.doFinal(key.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Request limit hashing is unavailable", exception);
        }
        tokens.ensureLimit(hash, window);
        return tokens.takeLimit(hash, window, maximum) == 1;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private boolean usable(MemberPasswordResetToken token, LocalDateTime now) {
        return token != null && token.getUsedAt() == null && token.getExpiresAt().isAfter(now);
    }

    public static class InvalidTokenException extends RuntimeException {}
    public static class TooManyAttemptsException extends RuntimeException {}
}
