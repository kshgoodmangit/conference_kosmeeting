package com.bjworld21.conference.repository;

import com.bjworld21.conference.entity.CommonCode;
import com.bjworld21.conference.entity.Country;
import com.bjworld21.conference.entity.Speaker;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface SpeakerRepository {
    String FILTER = """
            WHERE (#{keyword} = ''
                OR CONVERT(AES_DECRYPT(UNHEX(s.displayName), SHA2(#{dbEncString}, 512)) USING utf8mb4) LIKE CONCAT('%', #{keyword}, '%')
                OR CONVERT(AES_DECRYPT(UNHEX(s.displayNameKo), SHA2(#{dbEncString}, 512)) USING utf8mb4) LIKE CONCAT('%', #{keyword}, '%')
                OR s.affiliation LIKE CONCAT('%', #{keyword}, '%')
                OR CONVERT(AES_DECRYPT(UNHEX(s.contactEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4) LIKE CONCAT('%', #{keyword}, '%'))
              AND s.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND (#{typeCode} IS NULL OR s.speakerTypeCode = #{typeCode})
              AND (#{enabled} IS NULL OR s.enabled = #{enabled})
            """;
    String SELECT = """
            SELECT s.seq, s.speakerTypeCode,
                   CONVERT(AES_DECRYPT(UNHEX(s.displayName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS displayName,
                   CONVERT(AES_DECRYPT(UNHEX(s.displayNameKo), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS displayNameKo,
                   s.affiliation, s.department, s.positionTitle, s.countryCode, s.biography,
                   s.profileImageOriFilename, s.profileImageSaveFilename, s.homepageUrl,
                   CONVERT(AES_DECRYPT(UNHEX(s.contactEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS contactEmail,
                   s.featured, s.enabled, s.sortOrder, s.createdAt, s.updatedAt,
                   t.codeName AS speakerTypeName,
                   COALESCE(c.countryNameKo, c.countryName) AS countryName,
                   c.countryName AS countryNameEn
            FROM speakers s
            LEFT JOIN common_codes t ON t.seq = s.speakerTypeCode
            LEFT JOIN countries c ON c.isoAlpha2 = s.countryCode
            """;

    @Select("SELECT COUNT(*) FROM speakers s " + FILTER)
    long count(@Param("conferenceSeq") Long conferenceSeq,
               @Param("keyword") String keyword, @Param("typeCode") Long typeCode,
               @Param("enabled") Boolean enabled, @Param("dbEncString") String dbEncString);

    @Select("SELECT COUNT(*) FROM speakers s " + FILTER + " AND s.enabled = TRUE")
    long countEnabled(@Param("conferenceSeq") Long conferenceSeq,
                      @Param("keyword") String keyword, @Param("typeCode") Long typeCode,
                      @Param("enabled") Boolean enabled, @Param("dbEncString") String dbEncString);

    @Select("SELECT COUNT(*) FROM speakers s " + FILTER + " AND s.featured = TRUE")
    long countFeatured(@Param("conferenceSeq") Long conferenceSeq,
                       @Param("keyword") String keyword, @Param("typeCode") Long typeCode,
                       @Param("enabled") Boolean enabled, @Param("dbEncString") String dbEncString);

    @Select(SELECT + FILTER + " ORDER BY t.sortOrder, s.sortOrder, s.seq DESC LIMIT #{size} OFFSET #{offset}")
    List<Speaker> findPage(@Param("conferenceSeq") Long conferenceSeq,
                          @Param("keyword") String keyword, @Param("typeCode") Long typeCode,
                          @Param("enabled") Boolean enabled, @Param("size") int size, @Param("offset") long offset,
                          @Param("dbEncString") String dbEncString);

    @Select(SELECT + " WHERE s.seq = #{seq} AND s.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    Speaker findBySeq(@Param("conferenceSeq") Long conferenceSeq,
                      @Param("seq") Long seq, @Param("dbEncString") String dbEncString);

    @Select(SELECT + " WHERE s.seq = #{seq} AND s.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} FOR UPDATE")
    Speaker findBySeqForUpdate(@Param("conferenceSeq") Long conferenceSeq,
                               @Param("seq") Long seq, @Param("dbEncString") String dbEncString);

    @Select("""
            SELECT child.* FROM common_codes child
            JOIN common_codes root ON root.seq = child.parentSeq
            WHERE root.groupCode = 'SPEAKER_TYPE' AND root.parentSeq = 0
              AND root.isUsed = 'Y' AND root.isDelete = 'N'
              AND child.groupCode = 'SPEAKER_TYPE' AND child.isUsed = 'Y' AND child.isDelete = 'N'
            ORDER BY child.sortOrder, child.seq
            """)
    List<CommonCode> findTypes();

    @Select("SELECT * FROM countries WHERE isoAlpha2 = #{code}")
    Country findCountry(@Param("code") String code);

    @Insert("""
            INSERT INTO speakers (conferenceSeq, speakerTypeCode, displayName, displayNameKo, affiliation, department,
                positionTitle, countryCode, biography, profileImageOriFilename, profileImageSaveFilename,
                homepageUrl, contactEmail, featured, enabled, sortOrder)
            VALUES (#{conferenceSeq,javaType=java.lang.Long}, #{speaker.speakerTypeCode},
                HEX(AES_ENCRYPT(#{speaker.displayName}, SHA2(#{dbEncString}, 512))),
                HEX(AES_ENCRYPT(#{speaker.displayNameKo}, SHA2(#{dbEncString}, 512))),
                #{speaker.affiliation}, #{speaker.department}, #{speaker.positionTitle}, #{speaker.countryCode},
                #{speaker.biography}, #{speaker.profileImageOriFilename}, #{speaker.profileImageSaveFilename},
                #{speaker.homepageUrl}, HEX(AES_ENCRYPT(#{speaker.contactEmail}, SHA2(#{dbEncString}, 512))),
                #{speaker.featured}, #{speaker.enabled}, #{speaker.sortOrder})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "speaker.seq")
    void insert(@Param("conferenceSeq") Long conferenceSeq,
                @Param("speaker") Speaker speaker, @Param("dbEncString") String dbEncString);

    @Update("""
            UPDATE speakers SET speakerTypeCode = #{speaker.speakerTypeCode},
                displayName = HEX(AES_ENCRYPT(#{speaker.displayName}, SHA2(#{dbEncString}, 512))),
                displayNameKo = HEX(AES_ENCRYPT(#{speaker.displayNameKo}, SHA2(#{dbEncString}, 512))),
                affiliation = #{speaker.affiliation}, department = #{speaker.department},
                positionTitle = #{speaker.positionTitle}, countryCode = #{speaker.countryCode}, biography = #{speaker.biography},
                profileImageOriFilename = #{speaker.profileImageOriFilename}, profileImageSaveFilename = #{speaker.profileImageSaveFilename},
                homepageUrl = #{speaker.homepageUrl},
                contactEmail = HEX(AES_ENCRYPT(#{speaker.contactEmail}, SHA2(#{dbEncString}, 512))),
                featured = #{speaker.featured}, enabled = #{speaker.enabled}, sortOrder = #{speaker.sortOrder}, updatedAt = NOW()
            WHERE seq = #{speaker.seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    void update(@Param("conferenceSeq") Long conferenceSeq,
                @Param("speaker") Speaker speaker, @Param("dbEncString") String dbEncString);

    @Delete("DELETE FROM speakers WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    void delete(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);
}
