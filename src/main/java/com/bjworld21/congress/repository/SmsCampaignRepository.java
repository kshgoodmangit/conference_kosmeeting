package com.bjworld21.congress.repository;

import com.bjworld21.congress.dto.SmsData.*;
import com.bjworld21.congress.service.RecipientGroups;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface SmsCampaignRepository {
    String FILTER = " WHERE (#{keyword} = '' OR c.title LIKE CONCAT('%', #{keyword}, '%') OR c.message LIKE CONCAT('%', #{keyword}, '%') OR "
            + "CONVERT(AES_DECRYPT(UNHEX(c.senderNumber), SHA2(#{dbEncString}, 512)) USING utf8mb4) LIKE CONCAT('%', #{keyword}, '%')) "
            + "AND c.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} "
            + "AND (#{status} = '' OR c.status = #{status}) ";
    @Select("SELECT COUNT(*) AS totalCount, COALESCE(SUM(c.status='DRAFT'),0) AS draftCount, COALESCE(SUM(c.status='PREPARED'),0) AS preparedCount FROM sms_campaigns c" + FILTER)
    Summary summary(@Param("conferenceSeq") Long conferenceSeq,
                    @Param("keyword") String keyword, @Param("status") String status,
                    @Param("dbEncString") String dbEncString);
    @Select("""
            SELECT c.seq, c.title,
                   CONVERT(AES_DECRYPT(UNHEX(c.senderNumber), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS senderNumber,
                   c.message, c.messageType, c.status, c.scheduledAt, c.versionNo, c.createdBy, c.createdAt, c.updatedAt,
                   (SELECT COUNT(*) FROM sms_campaign_sources s WHERE s.campaignSeq=c.seq) AS sourceCount,
                   j.includedCount, j.excludedCount
            FROM sms_campaigns c LEFT JOIN sms_send_jobs j ON j.campaignSeq=c.seq
            """ + FILTER + " ORDER BY c.seq DESC LIMIT #{limit} OFFSET #{offset}")
    List<Campaign> findPage(@Param("conferenceSeq") Long conferenceSeq,
                            @Param("keyword") String keyword, @Param("status") String status, @Param("limit") int limit,
                            @Param("offset") int offset, @Param("dbEncString") String dbEncString);
    @Select("SELECT seq,title,CONVERT(AES_DECRYPT(UNHEX(senderNumber),SHA2(#{dbEncString},512)) USING utf8mb4) AS senderNumber,message,messageType,status,scheduledAt,versionNo,createdBy,createdAt,updatedAt FROM sms_campaigns WHERE seq=#{seq} AND conferenceSeq=#{conferenceSeq,javaType=java.lang.Long}")
    Campaign find(@Param("conferenceSeq") Long conferenceSeq,
                  @Param("seq") Long seq, @Param("dbEncString") String dbEncString);
    @Select("SELECT seq,title,CONVERT(AES_DECRYPT(UNHEX(senderNumber),SHA2(#{dbEncString},512)) USING utf8mb4) AS senderNumber,message,messageType,status,scheduledAt,versionNo,createdBy,createdAt,updatedAt FROM sms_campaigns WHERE seq=#{seq} AND conferenceSeq=#{conferenceSeq,javaType=java.lang.Long} FOR UPDATE")
    Campaign lock(@Param("conferenceSeq") Long conferenceSeq,
                  @Param("seq") Long seq, @Param("dbEncString") String dbEncString);
    @Insert("""
            INSERT INTO sms_campaigns(conferenceSeq,title,senderNumber,message,messageType,status,scheduledAt,versionNo,createdBy)
            VALUES(#{conferenceSeq,javaType=java.lang.Long},#{campaign.title},HEX(AES_ENCRYPT(#{campaign.senderNumber},SHA2(#{dbEncString},512))),
                   #{campaign.message},#{campaign.messageType},'DRAFT',#{campaign.scheduledAt},0,#{campaign.createdBy})
            """) @Options(useGeneratedKeys = true, keyProperty = "campaign.seq")
    void insert(@Param("conferenceSeq") Long conferenceSeq,
                @Param("campaign") Campaign campaign, @Param("dbEncString") String dbEncString);
    @Update("""
            UPDATE sms_campaigns SET title=#{campaign.title},
            senderNumber=HEX(AES_ENCRYPT(#{campaign.senderNumber},SHA2(#{dbEncString},512))),
            message=#{campaign.message},messageType=#{campaign.messageType},scheduledAt=#{campaign.scheduledAt},versionNo=versionNo+1
            WHERE seq=#{campaign.seq} AND conferenceSeq=#{conferenceSeq,javaType=java.lang.Long} AND status='DRAFT' AND versionNo=#{campaign.versionNo}
            """) int update(@Param("conferenceSeq") Long conferenceSeq,
                              @Param("campaign") Campaign campaign, @Param("dbEncString") String dbEncString);
    @Delete("DELETE FROM sms_campaigns WHERE seq=#{seq} AND conferenceSeq=#{conferenceSeq,javaType=java.lang.Long} AND status='DRAFT'") int delete(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);
    @Delete("DELETE FROM sms_campaign_sources WHERE campaignSeq=#{seq}") void deleteSources(Long seq);
    @Insert("""
            INSERT INTO sms_campaign_sources(campaignSeq,sourceType,groupCode,addressBookSeq,phoneNumber,fullName)
            VALUES(#{seq},#{source.sourceType},#{source.groupCode},#{source.addressBookSeq},
                   HEX(AES_ENCRYPT(#{source.phoneNumber},SHA2(#{dbEncString},512))),
                   HEX(AES_ENCRYPT(#{source.fullName},SHA2(#{dbEncString},512))))
            """) void insertSource(@Param("seq") Long seq, @Param("source") Source source,
                                     @Param("dbEncString") String dbEncString);
    @Select("SELECT sourceType,groupCode,addressBookSeq,CONVERT(AES_DECRYPT(UNHEX(phoneNumber),SHA2(#{dbEncString},512)) USING utf8mb4) AS phoneNumber,CONVERT(AES_DECRYPT(UNHEX(fullName),SHA2(#{dbEncString},512)) USING utf8mb4) AS fullName FROM sms_campaign_sources WHERE campaignSeq=#{seq} ORDER BY seq")
    List<Source> sources(@Param("seq") Long seq, @Param("dbEncString") String dbEncString);
    @Select("""
            SELECT m.seq AS memberSeq,
            """ + MemberPersonalDataSql.MOBILE + """
                   AS phoneNumber,
                   TRIM(CONCAT_WS(' ',
            """ + MemberPersonalDataSql.FIRST_NAME + """
                       ,
            """ + MemberPersonalDataSql.LAST_NAME + """
                   )) AS fullName
            FROM members m WHERE m.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} AND
            """ + RecipientGroups.MEMBER_PREDICATE + " ORDER BY m.seq")
    List<Candidate> group(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("group") String group,
            @Param("dbEncString") String dbEncString
    );
    @Select("""
            SELECT m.seq AS memberSeq,
            """ + MemberPersonalDataSql.MOBILE + """
                   AS phoneNumber,
                   TRIM(CONCAT_WS(' ',
            """ + MemberPersonalDataSql.FIRST_NAME + """
                       ,
            """ + MemberPersonalDataSql.LAST_NAME + """
                   )) AS fullName
            FROM members m WHERE CONCAT_WS(' ',
            """ + MemberPersonalDataSql.FIRST_NAME + """
                       ,
            """ + MemberPersonalDataSql.LAST_NAME + """
                       ,
            """ + MemberPersonalDataSql.EMAIL + """
                       ,
            """ + MemberPersonalDataSql.MOBILE + """
                       ,
                   m.institution) LIKE CONCAT('%',#{keyword},'%')
              AND m.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            ORDER BY m.seq DESC LIMIT 50
            """) List<Candidate> searchMembers(
                    @Param("conferenceSeq") Long conferenceSeq,
                    @Param("keyword") String keyword,
                    @Param("dbEncString") String dbEncString
            );
    @Select("""
            SELECT b.seq,b.addressBookName,COUNT(NULLIF(TRIM(CONVERT(AES_DECRYPT(UNHEX(c.phoneNumber),SHA2(#{dbEncString},512)) USING utf8mb4)),'')) AS contactCount
            FROM mail_address_books b LEFT JOIN mail_contacts c ON c.addressBookSeq=b.seq
            GROUP BY b.seq,b.addressBookName ORDER BY b.seq DESC
            """) List<AddressBook> addressBooks(@Param("dbEncString") String dbEncString);
    @Select("SELECT COUNT(*) FROM mail_address_books WHERE seq=#{seq}") int addressBookExists(Long seq);
    @Select("SELECT CONVERT(AES_DECRYPT(UNHEX(phoneNumber),SHA2(#{dbEncString},512)) USING utf8mb4) AS phoneNumber,CONVERT(AES_DECRYPT(UNHEX(fullName),SHA2(#{dbEncString},512)) USING utf8mb4) AS fullName FROM mail_contacts WHERE addressBookSeq=#{seq} ORDER BY seq")
    List<Candidate> contacts(@Param("seq") Long seq, @Param("dbEncString") String dbEncString);
    @Select("SELECT CONVERT(AES_DECRYPT(UNHEX(normalizedPhone),SHA2(#{dbEncString},512)) USING utf8mb4) FROM sms_suppression_list WHERE releasedAt IS NULL")
    List<String> suppressedPhones(@Param("dbEncString") String dbEncString);
    @Select("SELECT * FROM sms_send_jobs WHERE campaignSeq=#{seq}") Job job(Long seq);
    @Select("SELECT * FROM sms_send_jobs WHERE idempotencyKey=#{key}") Job jobByKey(String key);
    @Insert("""
            INSERT INTO sms_send_jobs(campaignSeq,idempotencyKey,provider,status,includedCount,excludedCount,duplicateCount,invalidCount,suppressionCount,scheduledAt)
            VALUES(#{campaignSeq},#{idempotencyKey},#{provider},'PREPARED',#{includedCount},#{excludedCount},#{duplicateCount},#{invalidCount},#{suppressionCount},#{scheduledAt})
            """) @Options(useGeneratedKeys = true, keyProperty = "seq") void insertJob(Job job);
    @Insert("""
            <script>INSERT INTO sms_campaign_recipients(jobSeq,phoneNumber,normalizedPhone,fullName,status,exclusionReason)
            VALUES <foreach collection="recipients" item="r" separator=",">
            (#{jobSeq},HEX(AES_ENCRYPT(#{r.phoneNumber},SHA2(#{dbEncString},512))),
             HEX(AES_ENCRYPT(#{r.normalizedPhone},SHA2(#{dbEncString},512))),
             HEX(AES_ENCRYPT(#{r.fullName},SHA2(#{dbEncString},512))),#{r.status},#{r.exclusionReason})
            </foreach></script>
            """) void insertRecipients(@Param("jobSeq") Long jobSeq, @Param("recipients") List<Recipient> recipients,
                                         @Param("dbEncString") String dbEncString);
    @Update("UPDATE sms_campaigns SET status='PREPARED',versionNo=versionNo+1 WHERE seq=#{seq} AND conferenceSeq=#{conferenceSeq,javaType=java.lang.Long} AND status='DRAFT'") int markPrepared(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);
    @Select("SELECT COUNT(*) FROM sms_campaign_recipients r JOIN sms_send_jobs j ON j.seq=r.jobSeq WHERE j.campaignSeq=#{seq}") long recipientCount(Long seq);
    @Select("""
            SELECT r.seq,
                   CONVERT(AES_DECRYPT(UNHEX(r.phoneNumber),SHA2(#{dbEncString},512)) USING utf8mb4) AS phoneNumber,
                   CONVERT(AES_DECRYPT(UNHEX(r.normalizedPhone),SHA2(#{dbEncString},512)) USING utf8mb4) AS normalizedPhone,
                   CONVERT(AES_DECRYPT(UNHEX(r.fullName),SHA2(#{dbEncString},512)) USING utf8mb4) AS fullName,
                   r.status,r.exclusionReason,r.providerMessageId,r.errorMessage
            FROM sms_campaign_recipients r JOIN sms_send_jobs j ON j.seq=r.jobSeq
            WHERE j.campaignSeq=#{seq} ORDER BY r.seq LIMIT #{limit} OFFSET #{offset}
            """) List<Recipient> recipients(@Param("seq") Long seq, @Param("limit") int limit,
                                              @Param("offset") int offset, @Param("dbEncString") String dbEncString);
}
