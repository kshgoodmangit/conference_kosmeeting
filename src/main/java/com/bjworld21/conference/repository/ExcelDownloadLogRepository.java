package com.bjworld21.conference.repository;

import com.bjworld21.conference.entity.ExcelDownloadLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ExcelDownloadLogRepository {

    @Insert("""
            INSERT INTO excel_download_logs (
                exportType, menuKey, menuName, reason, filterJson,
                adminSeq, adminEmail, adminName, adminRole,
                ipAddress, userAgent, status, requestedAt
            ) VALUES (
                #{log.exportType}, #{log.menuKey}, #{log.menuName}, #{log.reason}, #{log.filterJson},
                #{log.adminSeq},
                HEX(AES_ENCRYPT(#{log.adminEmail}, SHA2(#{dbEncString}, 512))),
                HEX(AES_ENCRYPT(#{log.adminName}, SHA2(#{dbEncString}, 512))),
                #{log.adminRole}, #{log.ipAddress}, #{log.userAgent}, 'PROCESSING', NOW(6)
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "log.seq")
    void insert(@Param("log") ExcelDownloadLog log, @Param("dbEncString") String dbEncString);

    @Update("""
            UPDATE excel_download_logs
            SET status = 'SUCCESS',
                rowCount = #{rowCount},
                fileName = #{fileName},
                fileSize = #{fileSize},
                failureCode = NULL,
                failureMessage = NULL,
                completedAt = NOW(6)
            WHERE seq = #{seq}
              AND status = 'PROCESSING'
            """)
    int markSuccess(
            @Param("seq") Long seq,
            @Param("rowCount") int rowCount,
            @Param("fileName") String fileName,
            @Param("fileSize") long fileSize
    );

    @Update("""
            UPDATE excel_download_logs
            SET status = 'FAILED',
                failureCode = #{failureCode},
                failureMessage = #{failureMessage},
                completedAt = NOW(6)
            WHERE seq = #{seq}
              AND status = 'PROCESSING'
            """)
    int markFailed(
            @Param("seq") Long seq,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage
    );

    @Select("""
            SELECT seq, exportType, menuKey, menuName, reason, filterJson, adminSeq,
                   CONVERT(AES_DECRYPT(UNHEX(adminEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS adminEmail,
                   CONVERT(AES_DECRYPT(UNHEX(adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS adminName,
                   adminRole, ipAddress, userAgent, status, rowCount, fileName, fileSize,
                   failureCode, failureMessage, requestedAt, completedAt
            FROM excel_download_logs WHERE seq = #{seq}
            """)
    ExcelDownloadLog findBySeq(@Param("seq") Long seq, @Param("dbEncString") String dbEncString);

    @Select("""
            <script>
            SELECT seq, exportType, menuKey, menuName, reason, filterJson, adminSeq,
                   CONVERT(AES_DECRYPT(UNHEX(adminEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS adminEmail,
                   CONVERT(AES_DECRYPT(UNHEX(adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS adminName,
                   adminRole, ipAddress, userAgent, status, rowCount, fileName, fileSize,
                   failureCode, failureMessage, requestedAt, completedAt
            FROM excel_download_logs
            <where>
                <if test="dateFrom != null">AND requestedAt &gt;= #{dateFrom}</if>
                <if test="dateTo != null">AND requestedAt &lt; DATE_ADD(#{dateTo}, INTERVAL 1 DAY)</if>
                <if test="exportType != null and exportType != ''">AND exportType = #{exportType}</if>
                <if test="status != null and status != ''">AND status = #{status}</if>
                <if test="adminKeyword != null and adminKeyword != ''">
                    AND (CONVERT(AES_DECRYPT(UNHEX(adminEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4)
                            LIKE CONCAT('%', #{adminKeyword}, '%')
                         OR CONVERT(AES_DECRYPT(UNHEX(adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4)
                            LIKE CONCAT('%', #{adminKeyword}, '%'))
                </if>
                <if test="reasonKeyword != null and reasonKeyword != ''">
                    AND reason LIKE CONCAT('%', #{reasonKeyword}, '%')
                </if>
            </where>
            ORDER BY requestedAt DESC, seq DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<ExcelDownloadLog> findPage(
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("exportType") String exportType,
            @Param("status") String status,
            @Param("adminKeyword") String adminKeyword,
            @Param("reasonKeyword") String reasonKeyword,
            @Param("size") int size,
            @Param("offset") int offset,
            @Param("dbEncString") String dbEncString
    );

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM excel_download_logs
            <where>
                <if test="dateFrom != null">AND requestedAt &gt;= #{dateFrom}</if>
                <if test="dateTo != null">AND requestedAt &lt; DATE_ADD(#{dateTo}, INTERVAL 1 DAY)</if>
                <if test="exportType != null and exportType != ''">AND exportType = #{exportType}</if>
                <if test="status != null and status != ''">AND status = #{status}</if>
                <if test="adminKeyword != null and adminKeyword != ''">
                    AND (CONVERT(AES_DECRYPT(UNHEX(adminEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4)
                            LIKE CONCAT('%', #{adminKeyword}, '%')
                         OR CONVERT(AES_DECRYPT(UNHEX(adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4)
                            LIKE CONCAT('%', #{adminKeyword}, '%'))
                </if>
                <if test="reasonKeyword != null and reasonKeyword != ''">
                    AND reason LIKE CONCAT('%', #{reasonKeyword}, '%')
                </if>
            </where>
            </script>
            """)
    long countPage(
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("exportType") String exportType,
            @Param("status") String status,
            @Param("adminKeyword") String adminKeyword,
            @Param("reasonKeyword") String reasonKeyword,
            @Param("dbEncString") String dbEncString
    );
}
