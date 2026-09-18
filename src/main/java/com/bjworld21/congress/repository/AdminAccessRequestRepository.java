package com.bjworld21.congress.repository;

import com.bjworld21.congress.entity.AdminAccessRequest;
import com.bjworld21.congress.entity.AdminAccessRequestMail;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AdminAccessRequestRepository {
    String EFFECTIVE_STATUS = "CASE WHEN r.status = 'REQUESTED' AND (r.expiresAt <= #{now} OR r.endDate < DATE(#{now})) THEN 'EXPIRED' ELSE r.status END";
    String COLUMNS = """
            SELECT r.seq, r.siteUrl, r.requestIp, r.startDate, r.endDate,
                r.createdAt, r.expiresAt, r.processedByAdminSeq, r.processedAt, r.allowlistSeq,
                CONVERT(AES_DECRYPT(UNHEX(r.affiliation), SHA2(#{key},512)) USING utf8mb4) AS affiliation,
                CONVERT(AES_DECRYPT(UNHEX(r.requesterName), SHA2(#{key},512)) USING utf8mb4) AS requesterName,
                CONVERT(AES_DECRYPT(UNHEX(r.contact), SHA2(#{key},512)) USING utf8mb4) AS contact,
                CONVERT(AES_DECRYPT(UNHEX(r.purpose), SHA2(#{key},512)) USING utf8mb4) AS purpose,
                (SELECT CONVERT(AES_DECRYPT(UNHEX(a.adminName), SHA2(#{key},512)) USING utf8mb4)
                 FROM admin_accounts a WHERE a.seq = r.processedByAdminSeq) AS processedByName,
                CASE
                  WHEN NOT EXISTS (SELECT 1 FROM admin_access_request_mail m WHERE m.requestSeq=r.seq) THEN 'NO_RECIPIENT'
                  WHEN EXISTS (SELECT 1 FROM admin_access_request_mail m WHERE m.requestSeq=r.seq AND m.status IN ('READY','SENDING')) THEN 'READY'
                  WHEN EXISTS (SELECT 1 FROM admin_access_request_mail m WHERE m.requestSeq=r.seq AND m.status='FAILED') THEN 'FAILED'
                  ELSE 'SENT' END AS mailStatus,
            """ + EFFECTIVE_STATUS + " AS status FROM admin_access_requests r ";
    String FILTER = """
            WHERE (#{keyword} = '' OR r.requestIp LIKE CONCAT('%',#{keyword},'%')
                OR CONVERT(AES_DECRYPT(UNHEX(r.requesterName),SHA2(#{key},512)) USING utf8mb4) LIKE CONCAT('%',#{keyword},'%')
                OR CONVERT(AES_DECRYPT(UNHEX(r.affiliation),SHA2(#{key},512)) USING utf8mb4) LIKE CONCAT('%',#{keyword},'%'))
            """ + " AND (#{status} = '' OR (" + EFFECTIVE_STATUS + ") = #{status}) ";

    // Unique site/IP row serializes concurrent submissions, including across JVMs.
    @Insert("INSERT INTO admin_access_request_locks(siteUrl,requestIp) VALUES(#{siteUrl},#{ip}) ON DUPLICATE KEY UPDATE seq=seq")
    void ensureLock(@Param("siteUrl") String siteUrl, @Param("ip") String ip);

    @Select("SELECT seq FROM admin_access_request_locks WHERE siteUrl=#{siteUrl} AND requestIp=#{ip} FOR UPDATE")
    Long lock(@Param("siteUrl") String siteUrl, @Param("ip") String ip);

    @Select("""
            SELECT COUNT(*) FROM admin_access_requests
            WHERE siteUrl=#{siteUrl} AND requestIp=#{ip} AND status='REQUESTED'
              AND expiresAt > #{now} AND endDate >= DATE(#{now})
            """)
    long countPending(@Param("siteUrl") String siteUrl, @Param("ip") String ip, @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM admin_access_requests WHERE siteUrl=#{siteUrl} AND requestIp=#{ip} AND createdAt >= #{since}")
    long countSince(@Param("siteUrl") String siteUrl, @Param("ip") String ip, @Param("since") LocalDateTime since);

    @Insert("""
            INSERT INTO admin_access_requests(siteUrl,requestIp,affiliation,requesterName,contact,purpose,
                startDate,endDate,status,createdAt,expiresAt)
            VALUES(#{r.siteUrl},#{r.requestIp},
                HEX(AES_ENCRYPT(#{r.affiliation},SHA2(#{key},512))),
                HEX(AES_ENCRYPT(#{r.requesterName},SHA2(#{key},512))),
                HEX(AES_ENCRYPT(#{r.contact},SHA2(#{key},512))),
                HEX(AES_ENCRYPT(#{r.purpose},SHA2(#{key},512))),
                #{r.startDate},#{r.endDate},'REQUESTED',#{r.createdAt},#{r.expiresAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "r.seq")
    void insert(@Param("r") AdminAccessRequest request, @Param("key") String key);

    @Select(COLUMNS + FILTER + " ORDER BY r.seq DESC LIMIT #{size} OFFSET #{offset}")
    List<AdminAccessRequest> findPage(Map<String,Object> params);

    @Select("SELECT COUNT(*) AS total, COALESCE(SUM((" + EFFECTIVE_STATUS + ")='REQUESTED'),0) AS pending, "
            + "COALESCE(SUM((" + EFFECTIVE_STATUS + ")='APPROVED'),0) AS approved FROM admin_access_requests r " + FILTER)
    Map<String,Object> summary(Map<String,Object> params);

    @Select(COLUMNS + " WHERE r.seq=#{seq}")
    AdminAccessRequest find(@Param("seq") Long seq, @Param("key") String key, @Param("now") LocalDateTime now);

    @Select("SELECT seq FROM admin_access_requests WHERE seq=#{seq} FOR UPDATE")
    Long lockRequest(@Param("seq") Long seq);

    @Update("UPDATE admin_access_requests SET status=#{status},allowlistSeq=#{allowlistSeq},processedByAdminSeq=#{adminSeq},processedAt=#{now} WHERE seq=#{seq} AND status='REQUESTED'")
    int process(@Param("seq") Long seq, @Param("status") String status, @Param("allowlistSeq") Long allowlistSeq,
                @Param("adminSeq") Long adminSeq, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO admin_access_request_mail(requestSeq,recipientEmail,status,attemptCount,nextAttemptAt)
            VALUES(#{requestSeq},HEX(AES_ENCRYPT(#{email},SHA2(#{key},512))),'READY',0,#{now})
            """)
    void enqueueMail(@Param("requestSeq") Long requestSeq, @Param("email") String email,
                     @Param("key") String key, @Param("now") LocalDateTime now);

    @Select("""
            SELECT seq FROM admin_access_request_mail
            WHERE (status='READY' AND nextAttemptAt <= #{now})
               OR (status='SENDING' AND leaseUntil <= #{now})
            ORDER BY seq LIMIT 20
            """)
    List<Long> dueMail(@Param("now") LocalDateTime now);

    @Update("""
            UPDATE admin_access_request_mail SET status='SENDING',attemptCount=attemptCount+1,claimToken=#{token},leaseUntil=#{lease}
            WHERE seq=#{seq} AND attemptCount < 3 AND
              ((status='READY' AND nextAttemptAt <= #{now}) OR (status='SENDING' AND leaseUntil <= #{now}))
            """)
    int claimMail(@Param("seq") Long seq, @Param("token") String token, @Param("now") LocalDateTime now, @Param("lease") LocalDateTime lease);

    @Select("""
            SELECT seq,requestSeq,attemptCount,claimToken,
              CONVERT(AES_DECRYPT(UNHEX(recipientEmail),SHA2(#{key},512)) USING utf8mb4) AS recipientEmail
            FROM admin_access_request_mail WHERE seq=#{seq}
            """)
    AdminAccessRequestMail findMail(@Param("seq") Long seq, @Param("key") String key);

    @Update("""
            UPDATE admin_access_request_mail SET status=#{status},nextAttemptAt=#{next},failureReason=#{reason},
              leaseUntil=NULL,claimToken=NULL WHERE seq=#{seq} AND claimToken=#{token}
            """)
    void finishMail(@Param("seq") Long seq, @Param("token") String token, @Param("status") String status,
                    @Param("next") LocalDateTime next, @Param("reason") String reason);

    @Update("UPDATE admin_access_request_mail SET status='FAILED',failureReason='발송 처리 중단' WHERE status='SENDING' AND attemptCount>=3 AND leaseUntil<=#{now}")
    void failAbandonedMail(@Param("now") LocalDateTime now);
}
