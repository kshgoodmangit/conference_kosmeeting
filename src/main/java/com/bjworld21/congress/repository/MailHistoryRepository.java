package com.bjworld21.congress.repository;

import com.bjworld21.congress.dto.MailHistoryData.*;
import com.bjworld21.congress.entity.MailSendJob;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface MailHistoryRepository {
    // Only the menu branch is selected dynamically; table names never come from client SQL.
    @Select("""
        <script>
        <choose>
          <when test="menu == 'speakers'">
            SELECT s.seq, CAST(s.seq AS CHAR) AS sourceLabel,
              CONVERT(AES_DECRYPT(UNHEX(s.contactEmail),SHA2(#{dbEncString},512)) USING utf8mb4) AS email,
              CONVERT(AES_DECRYPT(UNHEX(s.displayName),SHA2(#{dbEncString},512)) USING utf8mb4) AS fullName,
              s.affiliation FROM speakers s
          </when>
          <when test="menu == 'sponsorship'">
            SELECT s.seq, CAST(s.seq AS CHAR) AS sourceLabel,
              CONVERT(AES_DECRYPT(UNHEX(s.contactPersonEmail),SHA2(#{dbEncString},512)) USING utf8mb4) AS email,
              CONVERT(AES_DECRYPT(UNHEX(s.contactPersonName),SHA2(#{dbEncString},512)) USING utf8mb4) AS fullName,
              s.companyKrName AS affiliation FROM sponsorship_applications s
          </when>
          <otherwise>
            SELECT s.seq,
            <choose><when test="menu == 'pre-registrations'">s.registrationNumber</when><otherwise>s.submissionNo</otherwise></choose>
            AS sourceLabel,
        """ + MemberPersonalDataSql.EMAIL + " AS email, CONCAT_WS(' ', "
            + MemberPersonalDataSql.FIRST_NAME + ", " + MemberPersonalDataSql.LAST_NAME + ") AS fullName, m.institution AS affiliation " + """
            FROM
            <choose><when test="menu == 'pre-registrations'">pre_registrations</when><otherwise>abstract_submissions</otherwise></choose>
            s JOIN members m ON m.seq=s.memberSeq AND m.conferenceSeq=s.conferenceSeq
          </otherwise>
        </choose>
        WHERE s.conferenceSeq=#{conferenceSeq} AND s.seq IN
        <foreach collection="seqs" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY s.seq
        </script>
        """)
    List<Source> sources(@Param("conferenceSeq") Long conferenceSeq, @Param("menu") String menu,
                         @Param("seqs") List<Long> seqs, @Param("dbEncString") String dbEncString);

    @Select("SELECT CONVERT(AES_DECRYPT(UNHEX(adminName),SHA2(#{key},512)) USING utf8mb4) FROM admin_accounts WHERE seq=#{seq}")
    String adminName(@Param("seq") Long seq, @Param("key") String key);

    // Unique request key serializes concurrent retries until the first transaction commits or rolls back.
    @Insert("""
        INSERT INTO mail_history_contexts
          (conferenceSeq,sourceMenu,requestKey,requestHash,createdBy,adminName)
        VALUES (#{c.conferenceSeq},#{c.sourceMenu},#{c.requestKey},#{c.requestHash},#{c.createdBy},
          HEX(AES_ENCRYPT(#{c.adminName},SHA2(#{key},512))))
        ON DUPLICATE KEY UPDATE seq=LAST_INSERT_ID(seq)
        """)
    void reserve(@Param("c") Context context, @Param("key") String key);

    @Select("""
        SELECT seq,conferenceSeq,campaignSeq,jobSeq,createdBy,requestHash,sourceMenu,
          selectedCount,duplicateCount,invalidCount,suppressionCount
        FROM mail_history_contexts WHERE conferenceSeq=#{conferenceSeq} AND createdBy=#{adminSeq}
          AND requestKey=#{requestKey} FOR UPDATE
        """)
    Context lockRequest(@Param("conferenceSeq") Long conferenceSeq, @Param("adminSeq") Long adminSeq,
                        @Param("requestKey") String requestKey);

    @Insert("""
        INSERT INTO mail_send_jobs
          (campaignSeq,jobType,status,idempotencyKey,provider,totalCount,excludedCount,createdBy)
        VALUES (#{campaignSeq},'HISTORY','SAVED',#{requestKey},'none',#{total},#{excluded},#{adminSeq})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "job.seq")
    void insertJob(@Param("job") MailSendJob job, @Param("campaignSeq") Long campaignSeq,
                   @Param("requestKey") String requestKey, @Param("total") int total,
                   @Param("excluded") int excluded, @Param("adminSeq") Long adminSeq);

    @Update("""
        UPDATE mail_history_contexts SET campaignSeq=#{campaignSeq},jobSeq=#{jobSeq},
          selectedCount=#{selectedCount},duplicateCount=#{duplicateCount},invalidCount=#{invalidCount},
          suppressionCount=#{suppressionCount} WHERE seq=#{seq}
        """)
    void complete(Context context);

    @Insert("""
        INSERT INTO mail_recipient_origins(historySeq,recipientSeq,sourceType,sourceSeq,sourceLabel)
        VALUES(#{historySeq},#{recipientSeq},#{sourceType},#{sourceSeq},#{sourceLabel})
        """)
    void origin(@Param("historySeq") Long historySeq, @Param("recipientSeq") Long recipientSeq,
                @Param("sourceType") String sourceType, @Param("sourceSeq") Long sourceSeq,
                @Param("sourceLabel") String sourceLabel);

    String FROM = """
        FROM mail_history_contexts h JOIN mail_campaigns c ON c.seq=h.campaignSeq
        JOIN mail_send_jobs j ON j.seq=h.jobSeq
        """;
    String COMMON_FILTER = """
        WHERE h.conferenceSeq=#{f.conferenceSeq}
          AND (#{f.sourceMenu}='' OR h.sourceMenu=#{f.sourceMenu})
          AND (#{f.dateFrom} IS NULL OR h.createdAt &gt;= #{f.dateFrom})
          AND (#{f.dateTo} IS NULL OR h.createdAt &lt; DATE_ADD(#{f.dateTo},INTERVAL 1 DAY))
          AND (#{f.sourceSeq} IS NULL OR EXISTS (SELECT 1 FROM mail_recipient_origins o
            WHERE o.historySeq=h.seq AND o.sourceType=#{f.sourceType} AND o.sourceSeq=#{f.sourceSeq}))
        """;
    String FILTER = COMMON_FILTER + """
          AND (#{f.status}='' OR j.status=#{f.status})
          AND (#{f.keyword}='' OR c.subject LIKE CONCAT('%',#{f.keyword},'%')
            OR CONVERT(AES_DECRYPT(UNHEX(h.adminName),SHA2(#{key},512)) USING utf8mb4) LIKE CONCAT('%',#{f.keyword},'%')
            OR EXISTS (SELECT 1 FROM mail_campaign_recipients r WHERE r.jobSeq=h.jobSeq AND
              (CONVERT(AES_DECRYPT(UNHEX(r.email),SHA2(#{key},512)) USING utf8mb4) LIKE CONCAT('%',#{f.keyword},'%')
              OR CONVERT(AES_DECRYPT(UNHEX(r.fullName),SHA2(#{key},512)) USING utf8mb4) LIKE CONCAT('%',#{f.keyword},'%'))))
        """;
    String COLUMNS = """
        SELECT h.seq,h.campaignSeq,h.sourceMenu,c.subject,h.createdBy,
          CONVERT(AES_DECRYPT(UNHEX(h.adminName),SHA2(#{key},512)) USING utf8mb4) AS adminName,
          h.selectedCount,h.duplicateCount,h.invalidCount,h.suppressionCount,
          j.status,j.totalCount AS recipientCount,j.excludedCount,h.createdAt
        """;

    @Select("<script>SELECT COUNT(*) AS totalCount,COALESCE(SUM(j.status='SAVED'),0) AS savedCount,"
        + "COALESCE(SUM(j.totalCount),0) AS recipientCount " + FROM + FILTER + "</script>")
    Summary summary(@Param("f") Filter filter, @Param("key") String key);

    @Select("<script>" + COLUMNS + FROM + FILTER + " ORDER BY h.seq DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<Item> page(@Param("f") Filter filter, @Param("key") String key,
                    @Param("size") int size, @Param("offset") long offset);

    @Select(COLUMNS + ",c.htmlContent " + FROM + " WHERE h.conferenceSeq=#{conferenceSeq} AND h.seq=#{seq}")
    Item detail(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq, @Param("key") String key);

    @Select("""
        SELECT r.seq,
          CONVERT(AES_DECRYPT(UNHEX(r.email),SHA2(#{key},512)) USING utf8mb4) AS email,
          CONVERT(AES_DECRYPT(UNHEX(r.fullName),SHA2(#{key},512)) USING utf8mb4) AS fullName,
          r.affiliation,COALESCE(s.deliveryStatus,r.recipientStatus) AS status,r.exclusionReason,
          s.acceptedAt,s.deliveredAt,s.lastFailureReason AS failureReason
        FROM mail_history_contexts h JOIN mail_campaign_recipients r ON r.jobSeq=h.jobSeq
        LEFT JOIN mail_send_results s ON s.recipientSeq=r.seq
        WHERE h.conferenceSeq=#{conferenceSeq} AND h.seq=#{seq} ORDER BY r.seq
        """)
    List<Recipient> recipients(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq, @Param("key") String key);

    @Select("""
        SELECT o.recipientSeq,o.sourceType,o.sourceSeq,o.sourceLabel
        FROM mail_recipient_origins o JOIN mail_history_contexts h ON h.seq=o.historySeq
        WHERE h.conferenceSeq=#{conferenceSeq} AND h.seq=#{seq} ORDER BY o.seq
        """)
    List<Origin> origins(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);

    String EMAIL_FROM = FROM + """
        JOIN mail_campaign_recipients r ON r.jobSeq=h.jobSeq
        LEFT JOIN mail_send_results s ON s.recipientSeq=r.seq
        """;
    String EMAIL_FILTER = COMMON_FILTER + """
        AND (#{f.status}='' OR COALESCE(s.deliveryStatus,r.recipientStatus)=#{f.status})
        AND (#{f.exactEmail} IS NULL OR r.normalizedEmail=HEX(AES_ENCRYPT(#{f.exactEmail},SHA2(#{key},512))))
        AND (#{f.keyword}='' OR c.subject LIKE CONCAT('%',#{f.keyword},'%')
          OR CONVERT(AES_DECRYPT(UNHEX(h.adminName),SHA2(#{key},512)) USING utf8mb4) LIKE CONCAT('%',#{f.keyword},'%')
          OR CONVERT(AES_DECRYPT(UNHEX(r.email),SHA2(#{key},512)) USING utf8mb4) LIKE CONCAT('%',#{f.keyword},'%')
          OR CONVERT(AES_DECRYPT(UNHEX(r.fullName),SHA2(#{key},512)) USING utf8mb4) LIKE CONCAT('%',#{f.keyword},'%'))
        """;

    // Group the stored normalized address, so case/whitespace variants form one address across histories.
    @Select("<script>SELECT COUNT(DISTINCT r.normalizedEmail) AS totalCount,COUNT(*) AS historyCount,"
        + "COALESCE(SUM(COALESCE(s.deliveryStatus,r.recipientStatus)='SAVED'),0) AS savedCount,"
        + "COALESCE(SUM(COALESCE(s.deliveryStatus,r.recipientStatus)='EXCLUDED'),0) AS excludedCount "
        + EMAIL_FROM + EMAIL_FILTER + "</script>")
    EmailSummary emailSummary(@Param("f") Filter filter, @Param("key") String key);

    @Select("""
        <script>
        SELECT g.recipientSeq,
          CONVERT(AES_DECRYPT(UNHEX(latest.normalizedEmail),SHA2(#{key},512)) USING utf8mb4) AS email,
          CONVERT(AES_DECRYPT(UNHEX(latest.fullName),SHA2(#{key},512)) USING utf8mb4) AS fullName,
          g.historyCount,g.savedCount,g.excludedCount,g.lastCreatedAt
        FROM (SELECT MAX(r.seq) AS recipientSeq,COUNT(*) AS historyCount,
          SUM(COALESCE(s.deliveryStatus,r.recipientStatus)='SAVED') AS savedCount,
          SUM(COALESCE(s.deliveryStatus,r.recipientStatus)='EXCLUDED') AS excludedCount,
          MAX(h.createdAt) AS lastCreatedAt
        """ + EMAIL_FROM + EMAIL_FILTER + """
          GROUP BY r.normalizedEmail ORDER BY lastCreatedAt DESC,recipientSeq DESC LIMIT #{size} OFFSET #{offset}
        ) g JOIN mail_campaign_recipients latest ON latest.seq=g.recipientSeq
        ORDER BY g.lastCreatedAt DESC,g.recipientSeq DESC
        </script>
        """)
    List<EmailItem> emails(@Param("f") Filter filter, @Param("key") String key,
                           @Param("size") int size, @Param("offset") long offset);

    @Select("""
        SELECT CONVERT(AES_DECRYPT(UNHEX(r.normalizedEmail),SHA2(#{key},512)) USING utf8mb4)
        FROM mail_history_contexts h JOIN mail_campaign_recipients r ON r.jobSeq=h.jobSeq
        WHERE h.conferenceSeq=#{conferenceSeq} AND r.seq=#{recipientSeq}
        """)
    String recipientEmail(@Param("conferenceSeq") Long conferenceSeq, @Param("recipientSeq") Long recipientSeq,
                          @Param("key") String key);

    @Select("""
        <script>SELECT h.seq,r.seq AS recipientSeq,h.sourceMenu,c.subject,h.createdAt,
          CONVERT(AES_DECRYPT(UNHEX(h.adminName),SHA2(#{key},512)) USING utf8mb4) AS adminName,
          CONVERT(AES_DECRYPT(UNHEX(r.email),SHA2(#{key},512)) USING utf8mb4) AS email,
          CONVERT(AES_DECRYPT(UNHEX(r.fullName),SHA2(#{key},512)) USING utf8mb4) AS fullName,
          COALESCE(s.deliveryStatus,r.recipientStatus) AS status,r.exclusionReason,
          s.acceptedAt,s.deliveredAt,s.lastFailureReason AS failureReason
        """ + EMAIL_FROM + EMAIL_FILTER + " ORDER BY h.seq DESC,r.seq DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<EmailHistoryItem> emailHistories(@Param("f") Filter filter, @Param("key") String key,
                                         @Param("size") int size, @Param("offset") long offset);
}
