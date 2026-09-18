package com.bjworld21.congress.repository;

import com.bjworld21.congress.entity.CommonCode;
import com.bjworld21.congress.entity.Sponsor;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SponsorRepository {
    @Select("""
            SELECT COUNT(*)
            FROM sponsors sponsor
            JOIN common_codes typeCode ON typeCode.seq = sponsor.sponsorTypeCode
            WHERE sponsor.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND (
                #{keyword} IS NULL OR #{keyword} = ''
                OR LOWER(sponsor.sponsorName) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(sponsor.linkUrl) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(typeCode.codeName) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
            )
            """)
    long countByKeyword(@Param("conferenceSeq") Long conferenceSeq, @Param("keyword") String keyword);

    @Select("""
            SELECT COUNT(*)
            FROM sponsors sponsor
            JOIN common_codes typeCode ON typeCode.seq = sponsor.sponsorTypeCode
            WHERE sponsor.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND sponsor.enabled = TRUE
              AND (
                #{keyword} IS NULL OR #{keyword} = ''
                OR LOWER(sponsor.sponsorName) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(sponsor.linkUrl) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(typeCode.codeName) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              )
            """)
    long countEnabledByKeyword(@Param("conferenceSeq") Long conferenceSeq, @Param("keyword") String keyword);

    @Select("""
            SELECT sponsor.*,
                   typeCode.codeName AS sponsorTypeName
            FROM sponsors sponsor
            JOIN common_codes typeCode ON typeCode.seq = sponsor.sponsorTypeCode
            WHERE sponsor.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND (
                #{keyword} IS NULL OR #{keyword} = ''
                OR LOWER(sponsor.sponsorName) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(sponsor.linkUrl) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(typeCode.codeName) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
            )
            ORDER BY typeCode.sortOrder ASC, sponsor.sortOrder ASC, sponsor.seq DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Sponsor> findPage(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("keyword") String keyword,
            @Param("size") int size,
            @Param("offset") int offset
    );

    @Select("""
            SELECT sponsor.*,
                   typeCode.codeName AS sponsorTypeName
            FROM sponsors sponsor
            JOIN common_codes typeCode ON typeCode.seq = sponsor.sponsorTypeCode
            WHERE sponsor.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND sponsor.enabled = TRUE
              AND (sponsor.useStartDate IS NULL OR sponsor.useStartDate <= CURRENT_DATE)
              AND (sponsor.useEndDate IS NULL OR sponsor.useEndDate >= CURRENT_DATE)
              AND typeCode.isUsed = 'Y'
              AND typeCode.isDelete = 'N'
            ORDER BY typeCode.sortOrder ASC, sponsor.sortOrder ASC, sponsor.seq DESC
            """)
    List<Sponsor> findVisible(@Param("conferenceSeq") Long conferenceSeq);

    @Select("""
            SELECT sponsor.*,
                   typeCode.codeName AS sponsorTypeName
            FROM sponsors sponsor
            JOIN common_codes typeCode ON typeCode.seq = sponsor.sponsorTypeCode
            WHERE sponsor.seq = #{seq}
              AND sponsor.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    Sponsor findBySeq(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);

    @Select("""
            SELECT *
            FROM common_codes
            WHERE parentSeq = 9
              AND isUsed = 'Y'
              AND isDelete = 'N'
            ORDER BY sortOrder ASC, seq ASC
            """)
    List<CommonCode> findActiveSponsorTypes();

    @Select("""
            SELECT *
            FROM common_codes
            WHERE seq = #{seq}
              AND parentSeq = 9
              AND isUsed = 'Y'
              AND isDelete = 'N'
            """)
    CommonCode findActiveSponsorType(@Param("seq") Long seq);

    @Insert("""
            INSERT INTO sponsors (
                conferenceSeq, sponsorTypeCode, sponsorName, linkUrl,
                logoOriFilename, logoSaveFilename,
                useStartDate, useEndDate, enabled, sortOrder,
                createdAt, updatedAt
            ) VALUES (
                #{conferenceSeq,javaType=java.lang.Long}, #{sponsorTypeCode}, #{sponsorName}, #{linkUrl},
                #{logoOriFilename}, #{logoSaveFilename},
                #{useStartDate}, #{useEndDate}, #{enabled}, #{sortOrder},
                NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insert(Sponsor sponsor);

    @Update("""
            UPDATE sponsors
            SET sponsorTypeCode = #{sponsorTypeCode},
                sponsorName = #{sponsorName},
                linkUrl = #{linkUrl},
                logoOriFilename = #{logoOriFilename},
                logoSaveFilename = #{logoSaveFilename},
                useStartDate = #{useStartDate},
                useEndDate = #{useEndDate},
                enabled = #{enabled},
                sortOrder = #{sortOrder},
                updatedAt = NOW()
            WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    void update(Sponsor sponsor);

    @Delete("DELETE FROM sponsors WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    void delete(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);
}
