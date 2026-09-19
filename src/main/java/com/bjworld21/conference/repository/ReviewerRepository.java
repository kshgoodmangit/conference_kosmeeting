package com.bjworld21.conference.repository;

import com.bjworld21.conference.dto.AbstractCategoryResponse;
import com.bjworld21.conference.entity.ReviewerProfile;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ReviewerRepository {

    @Select("""
            SELECT reviewer.conferenceSeq
            FROM reviewers reviewer
            INNER JOIN conference_settings conference
                    ON conference.seq = reviewer.conferenceSeq
            WHERE reviewer.adminSeq = #{adminSeq}
              AND reviewer.isUsed = 'Y'
              AND reviewer.isDelete = 'N'
              AND conference.isLast = 'Y'
            ORDER BY reviewer.conferenceSeq DESC
            LIMIT 1
            """)
    Long findCurrentConferenceSeqByAdminSeq(@Param("adminSeq") Long adminSeq);

    @Select("""
            SELECT COUNT(*)
            FROM reviewers
            WHERE adminSeq = #{adminSeq}
              AND isUsed = 'Y'
              AND isDelete = 'N'
            """)
    long countActiveByAdminSeq(@Param("adminSeq") Long adminSeq);

    @Insert("""
            INSERT INTO reviewers (
                conferenceSeq, adminSeq, affiliation, department, positionTitle, phoneNumber, contactEmail,
                isUsed, isDelete, createdAt, updatedAt
            ) VALUES (
                #{conferenceSeq,javaType=java.lang.Long}, #{reviewer.adminSeq}, #{reviewer.affiliation}, #{reviewer.department}, #{reviewer.positionTitle},
                HEX(AES_ENCRYPT(#{reviewer.phoneNumber}, SHA2(#{dbEncString}, 512))),
                HEX(AES_ENCRYPT(#{reviewer.contactEmail}, SHA2(#{dbEncString}, 512))),
                #{reviewer.isUsed}, 'N', NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "reviewer.seq")
    void insert(@Param("conferenceSeq") Long conferenceSeq,
                @Param("reviewer") ReviewerProfile reviewerProfile,
                @Param("dbEncString") String dbEncString);

    @Update("""
            UPDATE reviewers
            SET affiliation = #{reviewer.affiliation},
                department = #{reviewer.department},
                positionTitle = #{reviewer.positionTitle},
                phoneNumber = HEX(AES_ENCRYPT(#{reviewer.phoneNumber}, SHA2(#{dbEncString}, 512))),
                contactEmail = HEX(AES_ENCRYPT(#{reviewer.contactEmail}, SHA2(#{dbEncString}, 512))),
                isUsed = #{reviewer.isUsed},
                isDelete = 'N',
                updatedAt = NOW()
            WHERE seq = #{reviewer.seq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    void update(@Param("conferenceSeq") Long conferenceSeq,
                @Param("reviewer") ReviewerProfile reviewerProfile,
                @Param("dbEncString") String dbEncString);

    @Select("""
            SELECT seq, conferenceSeq, adminSeq, affiliation, department, positionTitle,
                   CONVERT(AES_DECRYPT(UNHEX(phoneNumber), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS phoneNumber,
                   CONVERT(AES_DECRYPT(UNHEX(contactEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS contactEmail,
                   isUsed, isDelete, createdAt, updatedAt
            FROM reviewers
            WHERE adminSeq = #{adminSeq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND isDelete = 'N'
            """)
    ReviewerProfile findByAdminSeq(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("adminSeq") Long adminSeq,
            @Param("dbEncString") String dbEncString
    );

    @Select("SELECT categoryCode FROM reviewer_expertise WHERE reviewerSeq = #{reviewerSeq} ORDER BY seq")
    List<Long> findExpertiseCodes(Long reviewerSeq);

    @Delete("DELETE FROM reviewer_expertise WHERE reviewerSeq = #{reviewerSeq}")
    void deleteExpertise(Long reviewerSeq);

    @Insert({
            "<script>",
            "INSERT INTO reviewer_expertise (reviewerSeq, categoryCode, createdAt) VALUES",
            "<foreach collection='categoryCodes' item='categoryCode' separator=','>",
            "(#{reviewerSeq}, #{categoryCode}, NOW())",
            "</foreach>",
            "</script>"
    })
    void insertExpertise(
            @Param("reviewerSeq") Long reviewerSeq,
            @Param("categoryCodes") List<Long> categoryCodes
    );

    @Delete("DELETE FROM reviewers WHERE adminSeq = #{adminSeq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    void deleteByAdminSeq(@Param("conferenceSeq") Long conferenceSeq,
                          @Param("adminSeq") Long adminSeq);

    @Select("""
            SELECT COUNT(*)
            FROM common_codes c
            JOIN common_codes root
              ON root.seq = c.parentSeq
             AND root.parentSeq = 0
             AND root.groupCode = 'ABSTRACT_CATEGORY'
             AND root.isUsed = 'Y'
             AND root.isDelete = 'N'
            WHERE c.seq = #{categoryCode}
              AND c.groupCode = 'ABSTRACT_CATEGORY'
              AND c.isUsed = 'Y'
              AND c.isDelete = 'N'
            """)
    long countEnabledCategory(Long categoryCode);

    @Select("""
            SELECT
                c.seq AS code,
                c.codeName AS name,
                c.sortOrder,
                TRUE AS enabled
            FROM common_codes c
            JOIN common_codes root
              ON root.seq = c.parentSeq
             AND root.parentSeq = 0
             AND root.groupCode = 'ABSTRACT_CATEGORY'
             AND root.isUsed = 'Y'
             AND root.isDelete = 'N'
            WHERE c.groupCode = 'ABSTRACT_CATEGORY'
              AND c.isUsed = 'Y'
              AND c.isDelete = 'N'
            ORDER BY c.sortOrder, c.seq
            """)
    List<AbstractCategoryResponse> findEnabledCategories();
}
