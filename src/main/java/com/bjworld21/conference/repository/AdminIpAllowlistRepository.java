package com.bjworld21.conference.repository;

import com.bjworld21.conference.entity.AdminIpAllowlist;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AdminIpAllowlistRepository {

    String SELECT_COLUMNS = """
            SELECT rules.*,
                   CONVERT(AES_DECRYPT(UNHEX(createdAdmin.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS createdByAdminName,
                   CONVERT(AES_DECRYPT(UNHEX(updatedAdmin.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS updatedByAdminName
            FROM admin_ip_allowlist rules
            LEFT JOIN admin_accounts createdAdmin ON createdAdmin.seq = rules.createdByAdminSeq
            LEFT JOIN admin_accounts updatedAdmin ON updatedAdmin.seq = rules.updatedByAdminSeq
            """;

    @Select(SELECT_COLUMNS + " ORDER BY rules.enabled DESC, rules.ruleName ASC, rules.seq DESC")
    List<AdminIpAllowlist> findAll(@Param("dbEncString") String dbEncString);

    @Select(SELECT_COLUMNS + " WHERE rules.seq = #{seq}")
    AdminIpAllowlist findBySeq(@Param("seq") Long seq, @Param("dbEncString") String dbEncString);

    @Select("SELECT * FROM admin_ip_allowlist WHERE ipCidr = #{ipCidr} FOR UPDATE")
    AdminIpAllowlist findByIpCidrForUpdate(@Param("ipCidr") String ipCidr);

    @Select("""
            SELECT *
            FROM admin_ip_allowlist
            WHERE enabled = TRUE
            ORDER BY seq ASC
            """)
    List<AdminIpAllowlist> findEnabled();

    @Select("""
            SELECT COUNT(*)
            FROM admin_ip_allowlist
            WHERE ipCidr = #{ipCidr}
              AND (#{excludeSeq} IS NULL OR seq <> #{excludeSeq})
            """)
    long countByIpCidrExcludingSeq(
            @Param("ipCidr") String ipCidr,
            @Param("excludeSeq") Long excludeSeq
    );

    @Insert("""
            INSERT INTO admin_ip_allowlist (
                ruleName, ipCidr, description, useStartDate, useEndDate, enabled,
                createdByAdminSeq, updatedByAdminSeq, createdAt, updatedAt
            ) VALUES (
                #{ruleName}, #{ipCidr}, #{description}, #{useStartDate}, #{useEndDate}, #{enabled},
                #{createdByAdminSeq}, #{updatedByAdminSeq}, NOW(6), NOW(6)
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insert(AdminIpAllowlist rule);

    @Update("""
            UPDATE admin_ip_allowlist
            SET ruleName = #{ruleName},
                ipCidr = #{ipCidr},
                description = #{description},
                useStartDate = #{useStartDate},
                useEndDate = #{useEndDate},
                enabled = #{enabled},
                updatedByAdminSeq = #{updatedByAdminSeq},
                updatedAt = NOW(6)
            WHERE seq = #{seq}
            """)
    int update(AdminIpAllowlist rule);

    @Delete("DELETE FROM admin_ip_allowlist WHERE seq = #{seq}")
    int delete(@Param("seq") Long seq);
}
