package com.bjworld21.congress.repository;

import com.bjworld21.congress.dto.NormalizedMailEvent;
import com.bjworld21.congress.entity.MailProviderEvent;
import com.bjworld21.congress.entity.MailUnsubscribeTarget;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface MailEventRepository {

    @Insert("""
            INSERT IGNORE INTO mail_provider_events (
                provider, providerEventId, providerMessageId, eventType, recipientEmail,
                occurredAt, receivedAt, signatureVerified, processStatus, payloadJson, createdAt
            ) VALUES (
                #{event.provider}, #{event.providerEventId}, #{event.providerMessageId}, #{event.eventType},
                HEX(AES_ENCRYPT(#{event.recipientEmail}, SHA2(#{dbEncString}, 512))),
                #{event.occurredAt}, NOW(), #{event.signatureVerified}, 'RECEIVED', #{event.payloadJson}, NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "event.seq")
    int insertProviderEvent(@Param("event") MailProviderEvent event, @Param("dbEncString") String dbEncString);

    @Select("""
            SELECT r.seq
            FROM mail_send_results r
            WHERE r.provider=#{provider} AND r.providerMessageId=#{providerMessageId}
            LIMIT 1
            """)
    Long findSendResult(@Param("provider") String provider, @Param("providerMessageId") String providerMessageId);

    @Insert("""
            INSERT IGNORE INTO mail_recipient_events
                (providerEventSeq, sendResultSeq, eventType, occurredAt, clickedUrl, bounceType, eventMetadata, createdAt)
            VALUES
                (#{providerEventSeq}, #{sendResultSeq}, #{event.eventType}, #{event.occurredAt},
                 #{event.clickedUrl}, #{event.bounceType}, #{event.metadataJson}, NOW())
            """)
    int insertRecipientEvent(@Param("providerEventSeq") Long providerEventSeq,
                             @Param("sendResultSeq") Long sendResultSeq,
                             @Param("event") NormalizedMailEvent event);

    @Update("""
            UPDATE mail_provider_events
            SET processStatus=#{status}, processedAt=NOW(), errorMessage=#{errorMessage}
            WHERE seq=#{seq}
            """)
    void updateProviderEventStatus(@Param("seq") Long seq, @Param("status") String status,
                                   @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE mail_send_results
            SET providerMessageId=COALESCE(providerMessageId, #{providerMessageId}),
                deliveryStatus=CASE WHEN deliveryStatus IN ('PENDING','DEFERRED') THEN 'ACCEPTED' ELSE deliveryStatus END,
                acceptedAt=COALESCE(acceptedAt, #{occurredAt}), lastProviderEventAt=#{occurredAt}
            WHERE seq=#{resultSeq}
            """)
    void markAccepted(@Param("resultSeq") Long resultSeq, @Param("providerMessageId") String providerMessageId,
                      @Param("occurredAt") LocalDateTime occurredAt);

    @Update("""
            UPDATE mail_send_results
            SET deliveryStatus=CASE WHEN deliveryStatus IN ('PENDING','ACCEPTED','DEFERRED','SOFT_BOUNCE') THEN 'DELIVERED' ELSE deliveryStatus END,
                deliveredAt=COALESCE(deliveredAt, #{occurredAt}), lastProviderEventAt=#{occurredAt}
            WHERE seq=#{resultSeq}
            """)
    void markDelivered(@Param("resultSeq") Long resultSeq, @Param("occurredAt") LocalDateTime occurredAt);

    @Update("""
            UPDATE mail_send_results
            SET deliveryStatus=CASE WHEN deliveryStatus IN ('PENDING','ACCEPTED') THEN 'DEFERRED' ELSE deliveryStatus END,
                lastProviderEventAt=#{occurredAt}, lastFailureCode=#{failureCode}, lastFailureReason=#{failureReason}
            WHERE seq=#{resultSeq}
            """)
    void markDeferred(@Param("resultSeq") Long resultSeq, @Param("occurredAt") LocalDateTime occurredAt,
                      @Param("failureCode") String failureCode, @Param("failureReason") String failureReason);

    @Update("""
            UPDATE mail_send_results
            SET deliveryStatus=CASE WHEN deliveryStatus IN ('PENDING','ACCEPTED','DEFERRED') THEN 'SOFT_BOUNCE' ELSE deliveryStatus END,
                softBouncedAt=#{occurredAt}, lastProviderEventAt=#{occurredAt},
                lastFailureCode=#{failureCode}, lastFailureReason=#{failureReason}
            WHERE seq=#{resultSeq}
            """)
    void markSoftBounce(@Param("resultSeq") Long resultSeq, @Param("occurredAt") LocalDateTime occurredAt,
                        @Param("failureCode") String failureCode, @Param("failureReason") String failureReason);

    @Update("""
            UPDATE mail_send_results
            SET deliveryStatus='HARD_BOUNCE', hardBouncedAt=COALESCE(hardBouncedAt, #{occurredAt}),
                lastProviderEventAt=#{occurredAt}, lastFailureCode=#{failureCode}, lastFailureReason=#{failureReason}
            WHERE seq=#{resultSeq}
            """)
    void markHardBounce(@Param("resultSeq") Long resultSeq, @Param("occurredAt") LocalDateTime occurredAt,
                        @Param("failureCode") String failureCode, @Param("failureReason") String failureReason);

    @Update("""
            UPDATE mail_send_results
            SET deliveryStatus='COMPLAINT', complainedAt=COALESCE(complainedAt, #{occurredAt}), lastProviderEventAt=#{occurredAt}
            WHERE seq=#{resultSeq}
            """)
    void markComplaint(@Param("resultSeq") Long resultSeq, @Param("occurredAt") LocalDateTime occurredAt);

    @Update("""
            UPDATE mail_send_results
            SET firstOpenedAt=COALESCE(firstOpenedAt, #{occurredAt}), lastOpenedAt=#{occurredAt},
                openCount=openCount+1, lastProviderEventAt=#{occurredAt}
            WHERE seq=#{resultSeq}
            """)
    void markOpened(@Param("resultSeq") Long resultSeq, @Param("occurredAt") LocalDateTime occurredAt);

    @Update("""
            UPDATE mail_send_results
            SET firstClickedAt=COALESCE(firstClickedAt, #{occurredAt}), lastClickedAt=#{occurredAt},
                clickCount=clickCount+1, lastProviderEventAt=#{occurredAt}
            WHERE seq=#{resultSeq}
            """)
    void markClicked(@Param("resultSeq") Long resultSeq, @Param("occurredAt") LocalDateTime occurredAt);

    @Update("""
            UPDATE mail_send_results
            SET unsubscribedAt=COALESCE(unsubscribedAt, #{occurredAt}), lastProviderEventAt=#{occurredAt}
            WHERE seq=#{resultSeq}
            """)
    void markUnsubscribed(@Param("resultSeq") Long resultSeq, @Param("occurredAt") LocalDateTime occurredAt);

    @Select("""
            SELECT r.seq AS recipientSeq, r.campaignSeq,
                   CONVERT(AES_DECRYPT(UNHEX(r.email), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS email,
                   CONVERT(AES_DECRYPT(UNHEX(r.normalizedEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS normalizedEmail
            FROM mail_campaign_recipients r
            WHERE r.unsubscribeTokenHash=#{tokenHash}
            """)
    MailUnsubscribeTarget findUnsubscribeTarget(@Param("tokenHash") String tokenHash,
                                                @Param("dbEncString") String dbEncString);

    @Insert("""
            INSERT INTO mail_suppression_list
                (email, normalizedEmail, suppressionType, source, sourceCampaignSeq, sourceProvider, reason, suppressedAt, createdAt, updatedAt)
            VALUES
                (HEX(AES_ENCRYPT(#{email}, SHA2(#{dbEncString}, 512))),
                 HEX(AES_ENCRYPT(#{normalizedEmail}, SHA2(#{dbEncString}, 512))),
                 #{suppressionType}, #{source}, #{campaignSeq}, #{provider}, #{reason}, #{occurredAt}, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                email=VALUES(email), source=VALUES(source), sourceCampaignSeq=VALUES(sourceCampaignSeq),
                sourceProvider=VALUES(sourceProvider), reason=VALUES(reason), suppressedAt=VALUES(suppressedAt),
                releasedAt=NULL, releasedBy=NULL, releaseReason=NULL, updatedAt=NOW()
            """)
    void upsertSuppression(@Param("email") String email, @Param("normalizedEmail") String normalizedEmail,
                           @Param("suppressionType") String suppressionType, @Param("source") String source,
                           @Param("campaignSeq") Long campaignSeq, @Param("provider") String provider,
                           @Param("reason") String reason, @Param("occurredAt") LocalDateTime occurredAt,
                           @Param("dbEncString") String dbEncString);

    @Insert("""
            INSERT IGNORE INTO mail_unsubscribe_events
                (recipientSeq, email, normalizedEmail, unsubscribeSource, provider, providerEventSeq,
                 campaignSeq, unsubscribedAt, createdAt)
            VALUES
                (#{recipientSeq},
                 HEX(AES_ENCRYPT(#{email}, SHA2(#{dbEncString}, 512))),
                 HEX(AES_ENCRYPT(#{normalizedEmail}, SHA2(#{dbEncString}, 512))),
                 #{source}, #{provider}, #{providerEventSeq},
                 #{campaignSeq}, #{occurredAt}, NOW())
            """)
    void insertUnsubscribeEvent(@Param("recipientSeq") Long recipientSeq, @Param("email") String email,
                                @Param("normalizedEmail") String normalizedEmail, @Param("source") String source,
                                @Param("provider") String provider, @Param("providerEventSeq") Long providerEventSeq,
                                @Param("campaignSeq") Long campaignSeq, @Param("occurredAt") LocalDateTime occurredAt,
                                @Param("dbEncString") String dbEncString);
}
