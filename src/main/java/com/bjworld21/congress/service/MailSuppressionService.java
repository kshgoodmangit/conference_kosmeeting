package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.entity.MailUnsubscribeTarget;
import com.bjworld21.congress.repository.MailEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

@Service
public class MailSuppressionService {
    private final MailEventRepository repository;
    private final PersonalDataProperties personalDataProperties;

    public MailSuppressionService(MailEventRepository repository, PersonalDataProperties personalDataProperties) {
        this.repository = repository;
        this.personalDataProperties = personalDataProperties;
    }

    @Transactional
    public String unsubscribeByToken(String token) {
        MailUnsubscribeTarget target = repository.findUnsubscribeTarget(sha256(token), dbEncString());
        if (target == null) {
            throw new IllegalArgumentException("유효하지 않은 수신 거부 링크입니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        suppress(target.getEmail(), target.getNormalizedEmail(), "UNSUBSCRIBE", "SELF_SERVICE",
                target.getCampaignSeq(), null, "수신자가 수신 거부 링크를 사용했습니다.", now);
        repository.insertUnsubscribeEvent(target.getRecipientSeq(), target.getEmail(), target.getNormalizedEmail(),
                "SELF_SERVICE", null, null, target.getCampaignSeq(), now, dbEncString());
        return "수신 거부가 처리되었습니다.";
    }

    public void suppress(String email, String normalizedEmail, String type, String source, Long campaignSeq,
                         String provider, String reason, LocalDateTime occurredAt) {
        repository.upsertSuppression(
                email, normalizedEmail, type, source, campaignSeq, provider, reason, occurredAt, dbEncString()
        );
    }

    private String sha256(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
    private String dbEncString() {
        return personalDataProperties.requireDbEncString();
    }
}
