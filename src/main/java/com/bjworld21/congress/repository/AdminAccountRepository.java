package com.bjworld21.congress.repository;

import com.bjworld21.congress.entity.AdminAccount;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AdminAccountRepository {

    String SELECT_COLUMNS = """
            SELECT
                a.seq,
                CONVERT(AES_DECRYPT(UNHEX(a.email), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS email,
                a.password,
                CONVERT(AES_DECRYPT(UNHEX(a.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS adminName,
                a.affiliation,
                a.department,
                a.positionTitle,
                CONVERT(AES_DECRYPT(UNHEX(a.phoneNumber), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS phoneNumber,
                CONVERT(AES_DECRYPT(UNHEX(a.contactEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS contactEmail,
                a.role,
                a.status,
                a.lastLoginAt,
                a.loginFailureCount,
                a.loginLockedAt,
                a.createdAt,
                a.updatedAt
            FROM admin_accounts a
            """;

    String KEYWORD_FILTER = """
            WHERE (a.role <> 'reviewer' OR EXISTS (
                SELECT 1
                FROM reviewers reviewer
                WHERE reviewer.adminSeq = a.seq
                  AND reviewer.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
                  AND reviewer.isDelete = 'N'
            ))
              AND (
                #{keyword} IS NULL OR #{keyword} = ''
                OR LOWER(CONVERT(AES_DECRYPT(UNHEX(a.email), SHA2(#{dbEncString}, 512)) USING utf8mb4))
                    LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(CONVERT(AES_DECRYPT(UNHEX(a.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4))
                    LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(a.affiliation) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(a.department) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(a.positionTitle) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(CONVERT(AES_DECRYPT(UNHEX(a.contactEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4))
                    LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(a.role) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(a.status) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
            )
            """;

    @Insert("""
            INSERT INTO admin_accounts (
                email, password, adminName, affiliation, department, positionTitle,
                phoneNumber, contactEmail, role, status, createdAt, updatedAt
            )
            VALUES (
                HEX(AES_ENCRYPT(LOWER(TRIM(#{account.email})), SHA2(#{dbEncString}, 512))),
                #{account.password},
                HEX(AES_ENCRYPT(#{account.adminName}, SHA2(#{dbEncString}, 512))),
                #{account.affiliation},
                #{account.department},
                #{account.positionTitle},
                HEX(AES_ENCRYPT(#{account.phoneNumber}, SHA2(#{dbEncString}, 512))),
                HEX(AES_ENCRYPT(LOWER(TRIM(#{account.contactEmail})), SHA2(#{dbEncString}, 512))),
                #{account.role}, #{account.status}, NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "account.seq")
    void insert(@Param("account") AdminAccount account, @Param("dbEncString") String dbEncString);

    @Select(SELECT_COLUMNS + " WHERE a.email = HEX(AES_ENCRYPT(LOWER(TRIM(#{email})), SHA2(#{dbEncString}, 512)))")
    AdminAccount findByEmail(@Param("email") String email, @Param("dbEncString") String dbEncString);

    @Select(SELECT_COLUMNS + " WHERE a.email = HEX(AES_ENCRYPT(LOWER(TRIM(#{email})), SHA2(#{dbEncString}, 512))) FOR UPDATE")
    AdminAccount findByEmailForUpdate(@Param("email") String email, @Param("dbEncString") String dbEncString);

    @Update("""
            UPDATE admin_accounts
            SET loginFailureCount = #{failureCount},
                loginLockedAt = CASE WHEN #{failureCount} >= 5 THEN CURRENT_TIMESTAMP ELSE NULL END
            WHERE seq = #{seq}
            """)
    void recordLoginFailure(@Param("seq") Long seq, @Param("failureCount") int failureCount);

    @Update("UPDATE admin_accounts SET loginFailureCount = 0, loginLockedAt = NULL WHERE seq = #{seq}")
    void clearLoginFailures(@Param("seq") Long seq);

    @Select(SELECT_COLUMNS + " WHERE a.seq = #{seq}")
    AdminAccount findBySeq(@Param("seq") Long seq, @Param("dbEncString") String dbEncString);

    @Select(SELECT_COLUMNS + " WHERE a.status = 'active' ORDER BY a.createdAt DESC")
    List<AdminAccount> findAllActive(@Param("dbEncString") String dbEncString);

    @Select(SELECT_COLUMNS + " WHERE a.status = 'active' AND a.role = #{role} ORDER BY a.seq")
    List<AdminAccount> findAllActiveByRole(
            @Param("role") String role,
            @Param("dbEncString") String dbEncString
    );

    @Select(SELECT_COLUMNS + " ORDER BY a.createdAt DESC")
    List<AdminAccount> findAll(@Param("dbEncString") String dbEncString);

    @Select(SELECT_COLUMNS + KEYWORD_FILTER + " ORDER BY a.createdAt DESC, a.seq DESC LIMIT #{size} OFFSET #{offset}")
    List<AdminAccount> findPage(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("keyword") String keyword,
            @Param("size") int size,
            @Param("offset") int offset,
            @Param("dbEncString") String dbEncString
    );

    @Select("SELECT COUNT(*) FROM admin_accounts a " + KEYWORD_FILTER)
    long countByKeyword(@Param("conferenceSeq") Long conferenceSeq,
                        @Param("keyword") String keyword,
                        @Param("dbEncString") String dbEncString);

    @Select("SELECT COUNT(*) FROM admin_accounts a " + KEYWORD_FILTER + " AND a.role = #{role}")
    long countByKeywordAndRole(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("keyword") String keyword,
            @Param("role") String role,
            @Param("dbEncString") String dbEncString
    );

    @Select(SELECT_COLUMNS + KEYWORD_FILTER + " ORDER BY a.createdAt DESC, a.seq DESC")
    List<AdminAccount> findAllForExport(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("keyword") String keyword,
            @Param("dbEncString") String dbEncString
    );

    @Select("SELECT COUNT(*) FROM admin_accounts")
    long count();

    @Update("""
            UPDATE admin_accounts
            SET adminName = HEX(AES_ENCRYPT(#{account.adminName}, SHA2(#{dbEncString}, 512))),
                affiliation = #{account.affiliation},
                department = #{account.department},
                positionTitle = #{account.positionTitle},
                phoneNumber = HEX(AES_ENCRYPT(#{account.phoneNumber}, SHA2(#{dbEncString}, 512))),
                contactEmail = HEX(AES_ENCRYPT(LOWER(TRIM(#{account.contactEmail})), SHA2(#{dbEncString}, 512))),
                role = #{account.role},
                status = #{account.status},
                updatedAt = NOW()
            WHERE seq = #{account.seq}
            """)
    void update(@Param("account") AdminAccount account, @Param("dbEncString") String dbEncString);

    @Update("UPDATE admin_accounts SET lastLoginAt = NOW() WHERE seq = #{seq}")
    void updateLastLoginAt(Long seq);

    @Update("UPDATE admin_accounts SET password = #{password}, updatedAt = NOW() WHERE seq = #{seq}")
    void updatePassword(AdminAccount adminAccount);

    @Update("UPDATE admin_accounts SET password = #{password}, loginFailureCount = 0, loginLockedAt = NULL, updatedAt = NOW() WHERE seq = #{seq}")
    void resetPasswordAndLoginFailures(AdminAccount adminAccount);

    @Update("""
            UPDATE admin_accounts
            SET adminName = HEX(AES_ENCRYPT(#{account.adminName}, SHA2(#{dbEncString}, 512))),
                affiliation = #{account.affiliation},
                department = #{account.department},
                positionTitle = #{account.positionTitle},
                phoneNumber = HEX(AES_ENCRYPT(#{account.phoneNumber}, SHA2(#{dbEncString}, 512))),
                contactEmail = HEX(AES_ENCRYPT(LOWER(TRIM(#{account.contactEmail})), SHA2(#{dbEncString}, 512))),
                updatedAt = NOW()
            WHERE seq = #{account.seq}
            """)
    void updateOwnProfile(
            @Param("account") AdminAccount account,
            @Param("dbEncString") String dbEncString
    );

    @Delete("DELETE FROM admin_accounts WHERE seq = #{seq}")
    void delete(Long seq);
}
