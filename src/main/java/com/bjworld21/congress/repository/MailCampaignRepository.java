package com.bjworld21.congress.repository;

import com.bjworld21.congress.dto.MailCampaignSummaryResponse;
import com.bjworld21.congress.dto.MailDirectRecipientRequest;
import com.bjworld21.congress.entity.MailAttachment;
import com.bjworld21.congress.entity.MailCampaign;
import com.bjworld21.congress.entity.MailRecipientCandidate;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MailCampaignRepository {

    @Select("""
            SELECT COUNT(*) FROM mail_campaigns
            WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND NOT EXISTS (SELECT 1 FROM mail_history_contexts h WHERE h.campaignSeq=mail_campaigns.seq)
              AND (#{keyword} = '' OR LOWER(subject) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            """)
    long count(@Param("conferenceSeq") Long conferenceSeq, @Param("keyword") String keyword);

    @Select("""
            SELECT c.seq, c.subject,
                   CONVERT(AES_DECRYPT(UNHEX(c.senderName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS senderName,
                   CONVERT(AES_DECRYPT(UNHEX(c.senderEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS senderEmail,
                   c.mailType, c.status,
                   c.scheduledAt, c.createdAt, c.updatedAt,
                   (SELECT COUNT(*) FROM mail_campaign_recipient_sources s WHERE s.campaignSeq = c.seq) AS sourceCount,
                   (SELECT COUNT(*) FROM mail_campaign_attachments a WHERE a.campaignSeq = c.seq) AS attachmentCount
            FROM mail_campaigns c
            WHERE c.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND NOT EXISTS (SELECT 1 FROM mail_history_contexts h WHERE h.campaignSeq=c.seq)
              AND (#{keyword} = '' OR LOWER(c.subject) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            ORDER BY c.createdAt DESC, c.seq DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<MailCampaignSummaryResponse> findPage(@Param("conferenceSeq") Long conferenceSeq,
                                               @Param("keyword") String keyword, @Param("size") int size,
                                               @Param("offset") int offset, @Param("dbEncString") String dbEncString);

    @Select("""
            SELECT seq, subject,
                   CONVERT(AES_DECRYPT(UNHEX(senderName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS senderName,
                   CONVERT(AES_DECRYPT(UNHEX(senderEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS senderEmail,
                   CONVERT(AES_DECRYPT(UNHEX(replyToEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS replyToEmail,
                   htmlContent, textContent, mailType, status, trackOpens, trackClicks,
                   scheduledAt, versionNo, createdBy, createdAt, updatedAt
            FROM mail_campaigns WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    MailCampaign findBySeq(@Param("conferenceSeq") Long conferenceSeq,
                           @Param("seq") Long seq, @Param("dbEncString") String dbEncString);

    @Insert("""
            INSERT INTO mail_campaigns (
                conferenceSeq, subject, senderName, senderEmail, replyToEmail, htmlContent, textContent,
                mailType, status, trackOpens, trackClicks, scheduledAt, versionNo, createdBy, createdAt, updatedAt
            ) VALUES (
                #{conferenceSeq,javaType=java.lang.Long}, #{campaign.subject},
                HEX(AES_ENCRYPT(#{campaign.senderName}, SHA2(#{dbEncString}, 512))),
                HEX(AES_ENCRYPT(#{campaign.senderEmail}, SHA2(#{dbEncString}, 512))),
                HEX(AES_ENCRYPT(#{campaign.replyToEmail}, SHA2(#{dbEncString}, 512))),
                #{campaign.htmlContent}, #{campaign.textContent}, #{campaign.mailType}, #{campaign.status},
                #{campaign.trackOpens}, #{campaign.trackClicks}, #{campaign.scheduledAt}, 0,
                #{campaign.createdBy}, NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "campaign.seq")
    void insert(@Param("conferenceSeq") Long conferenceSeq,
                @Param("campaign") MailCampaign campaign, @Param("dbEncString") String dbEncString);

    @Update("""
            UPDATE mail_campaigns
            SET subject=#{campaign.subject},
                senderName=HEX(AES_ENCRYPT(#{campaign.senderName}, SHA2(#{dbEncString}, 512))),
                senderEmail=HEX(AES_ENCRYPT(#{campaign.senderEmail}, SHA2(#{dbEncString}, 512))),
                replyToEmail=HEX(AES_ENCRYPT(#{campaign.replyToEmail}, SHA2(#{dbEncString}, 512))),
                htmlContent=#{campaign.htmlContent}, textContent=#{campaign.textContent}, mailType=#{campaign.mailType},
                trackOpens=#{campaign.trackOpens}, trackClicks=#{campaign.trackClicks}, scheduledAt=#{campaign.scheduledAt},
                versionNo=versionNo+1, updatedAt=NOW()
            WHERE seq=#{campaign.seq} AND conferenceSeq=#{conferenceSeq,javaType=java.lang.Long} AND versionNo=#{campaign.versionNo} AND status='DRAFT'
            """)
    int update(@Param("conferenceSeq") Long conferenceSeq,
               @Param("campaign") MailCampaign campaign, @Param("dbEncString") String dbEncString);

    @Delete("DELETE FROM mail_campaigns WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} AND status = 'DRAFT'")
    int delete(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);

    @Delete("DELETE FROM mail_campaign_recipient_sources WHERE campaignSeq = #{campaignSeq}")
    void deleteSources(Long campaignSeq);

    @Insert("""
            INSERT INTO mail_campaign_recipient_sources (campaignSeq, sourceType, addressBookSeq)
            VALUES (#{campaignSeq}, 'ADDRESS_BOOK', #{addressBookSeq})
            """)
    void insertAddressBookSource(@Param("campaignSeq") Long campaignSeq, @Param("addressBookSeq") Long addressBookSeq);

    @Insert("""
            INSERT INTO mail_campaign_recipient_sources
                (campaignSeq, sourceType, email, normalizedEmail, fullName, affiliation)
            VALUES
                (#{campaignSeq}, #{recipient.sourceType},
                 HEX(AES_ENCRYPT(#{recipient.email}, SHA2(#{dbEncString}, 512))),
                 HEX(AES_ENCRYPT(#{normalizedEmail}, SHA2(#{dbEncString}, 512))),
                 HEX(AES_ENCRYPT(#{recipient.fullName}, SHA2(#{dbEncString}, 512))),
                 #{recipient.affiliation})
            """)
    void insertDirectSource(@Param("campaignSeq") Long campaignSeq,
                            @Param("recipient") MailDirectRecipientRequest recipient,
                            @Param("normalizedEmail") String normalizedEmail,
                            @Param("dbEncString") String dbEncString);

    @Insert("INSERT INTO mail_campaign_recipient_sources (campaignSeq, sourceType) VALUES (#{campaignSeq}, #{group})")
    void insertGroupSource(@Param("campaignSeq") Long campaignSeq, @Param("group") String group);

    @Select("SELECT sourceType FROM mail_campaign_recipient_sources WHERE campaignSeq=#{campaignSeq} AND sourceType IN ('ALL_MEMBERS', 'ALL_REGISTRANTS', 'ALL_SUBMITTERS', 'ALL_ACCEPTED') ORDER BY seq")
    List<String> findGroupSources(Long campaignSeq);

    @Select("SELECT addressBookSeq FROM mail_campaign_recipient_sources WHERE campaignSeq=#{campaignSeq} AND sourceType='ADDRESS_BOOK' ORDER BY seq")
    List<Long> findAddressBookSources(Long campaignSeq);

    @Select("""
            SELECT sourceType,
                   CONVERT(AES_DECRYPT(UNHEX(email), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS email,
                   CONVERT(AES_DECRYPT(UNHEX(fullName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS fullName,
                   affiliation
            FROM mail_campaign_recipient_sources
            WHERE campaignSeq=#{campaignSeq} AND sourceType IN ('DIRECT', 'INTERNAL_MEMBER', 'INTERNAL_ADMIN', 'ADDRESS_BOOK_CONTACT')
            ORDER BY seq
            """)
    List<MailDirectRecipientRequest> findDirectSources(@Param("campaignSeq") Long campaignSeq,
                                                       @Param("dbEncString") String dbEncString);

    @Select("""
            SELECT c.seq AS contactSeq,
                   CONVERT(AES_DECRYPT(UNHEX(c.email), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS email,
                   CONVERT(AES_DECRYPT(UNHEX(c.normalizedEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS normalizedEmail,
                   CONVERT(AES_DECRYPT(UNHEX(c.fullName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS fullName,
                   c.affiliation
            FROM mail_campaign_recipient_sources s
            JOIN mail_address_books b ON b.seq=s.addressBookSeq
            JOIN mail_contacts c ON c.addressBookSeq=b.seq
            WHERE s.campaignSeq=#{campaignSeq} AND s.sourceType='ADDRESS_BOOK'
            UNION ALL
            SELECT NULL,
                   CONVERT(AES_DECRYPT(UNHEX(s.email), SHA2(#{dbEncString}, 512)) USING utf8mb4),
                   CONVERT(AES_DECRYPT(UNHEX(s.normalizedEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4),
                   CONVERT(AES_DECRYPT(UNHEX(s.fullName), SHA2(#{dbEncString}, 512)) USING utf8mb4),
                   s.affiliation
            FROM mail_campaign_recipient_sources s
            WHERE s.campaignSeq=#{campaignSeq} AND s.sourceType IN ('DIRECT', 'INTERNAL_MEMBER', 'INTERNAL_ADMIN', 'ADDRESS_BOOK_CONTACT')
            """)
    List<MailRecipientCandidate> findRecipientCandidates(@Param("campaignSeq") Long campaignSeq,
                                                         @Param("dbEncString") String dbEncString);

    @Select("SELECT * FROM mail_campaign_attachments WHERE campaignSeq=#{campaignSeq} ORDER BY seq")
    List<MailAttachment> findAttachments(Long campaignSeq);

    @Select("SELECT * FROM mail_campaign_attachments WHERE seq=#{seq}")
    MailAttachment findAttachment(Long seq);

    @Select("SELECT COUNT(*) FROM mail_campaign_attachments WHERE campaignSeq=#{campaignSeq}")
    int countAttachments(Long campaignSeq);

    @Select("SELECT COALESCE(SUM(fileSize), 0) FROM mail_campaign_attachments WHERE campaignSeq=#{campaignSeq}")
    long sumAttachmentBytes(Long campaignSeq);

    @Insert("""
            INSERT INTO mail_campaign_attachments
                (campaignSeq, originalFilename, savedFilename, contentType, fileSize, createdAt)
            VALUES (#{campaignSeq}, #{originalFilename}, #{savedFilename}, #{contentType}, #{fileSize}, NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insertAttachment(MailAttachment attachment);

    @Delete("DELETE FROM mail_campaign_attachments WHERE seq=#{seq}")
    int deleteAttachment(Long seq);

    @Select("SELECT CONVERT(AES_DECRYPT(UNHEX(normalizedEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4) FROM mail_suppression_list WHERE releasedAt IS NULL")
    List<String> findActiveSuppressions(@Param("dbEncString") String dbEncString);

    @Select("SELECT seq FROM mail_send_jobs WHERE idempotencyKey=#{idempotencyKey}")
    Long findJobByIdempotencyKey(String idempotencyKey);

    @Insert("""
            INSERT INTO mail_send_jobs
                (campaignSeq, status, idempotencyKey, provider, totalCount, excludedCount, scheduledAt, createdAt, updatedAt)
            VALUES
                (#{campaignSeq}, 'QUEUED', #{idempotencyKey}, #{provider}, #{totalCount}, #{excludedCount}, #{scheduledAt}, NOW(), NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "job.seq")
    void insertJob(@Param("job") com.bjworld21.congress.entity.MailSendJob job,
                   @Param("campaignSeq") Long campaignSeq,
                   @Param("idempotencyKey") String idempotencyKey,
                   @Param("provider") String provider,
                   @Param("totalCount") int totalCount,
                   @Param("excludedCount") int excludedCount,
                   @Param("scheduledAt") LocalDateTime scheduledAt);

    @Insert("""
            INSERT INTO mail_campaign_recipients
                (jobSeq, campaignSeq, contactSeq, email, normalizedEmail, fullName, affiliation,
                 recipientStatus, exclusionReason, unsubscribeToken, unsubscribeTokenHash, createdAt)
            VALUES
                (#{jobSeq}, #{campaignSeq}, #{candidate.contactSeq},
                 HEX(AES_ENCRYPT(#{candidate.email}, SHA2(#{dbEncString}, 512))),
                 HEX(AES_ENCRYPT(#{candidate.normalizedEmail}, SHA2(#{dbEncString}, 512))),
                 HEX(AES_ENCRYPT(#{candidate.fullName}, SHA2(#{dbEncString}, 512))),
                 #{candidate.affiliation}, #{status}, #{exclusionReason}, #{token}, #{tokenHash}, NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "recipient.seq")
    void insertRecipient(@Param("recipient") com.bjworld21.congress.entity.MailCampaignRecipient recipient,
                         @Param("jobSeq") Long jobSeq,
                         @Param("campaignSeq") Long campaignSeq,
                         @Param("candidate") MailRecipientCandidate candidate,
                         @Param("status") String status,
                         @Param("exclusionReason") String exclusionReason,
                         @Param("token") String token,
                         @Param("tokenHash") String tokenHash,
                         @Param("dbEncString") String dbEncString);

    @Insert("INSERT INTO mail_send_results (recipientSeq, provider, deliveryStatus) VALUES (#{recipientSeq}, #{provider}, #{status})")
    void insertSendResult(@Param("recipientSeq") Long recipientSeq, @Param("provider") String provider, @Param("status") String status);

    @Update("UPDATE mail_campaigns SET status=#{status}, updatedAt=NOW() WHERE seq=#{seq} AND conferenceSeq=#{conferenceSeq,javaType=java.lang.Long} AND status='DRAFT'")
    int updateStatus(@Param("conferenceSeq") Long conferenceSeq,
                     @Param("seq") Long seq, @Param("status") String status);
}
