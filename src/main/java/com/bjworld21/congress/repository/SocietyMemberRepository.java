package com.bjworld21.congress.repository;

import com.bjworld21.congress.dto.SocietyMemberData.*;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface SocietyMemberRepository {
    String FILTER = """
            FROM society_members m
            <where>
                <if test="keyword != ''">
                    (LOCATE(#{keyword}, m.licenseNumber) > 0
                     OR LOCATE(#{keyword}, CONVERT(AES_DECRYPT(UNHEX(m.fullName), SHA2(#{dbEncString}, 512)) USING utf8mb4)) > 0
                     OR LOCATE(#{keyword}, m.affiliation) > 0)
                </if>
                <if test="memberType != ''">AND m.memberType = #{memberType}</if>
            </where>
            """;

    @Select("<script>SELECT COUNT(*) AS totalCount, COALESCE(SUM(memberType = '정회원'), 0) AS regularCount, "
            + "COALESCE(SUM(memberType = '준회원'), 0) AS associateCount, COALESCE(SUM(memberType = '기타'), 0) AS otherCount "
            + FILTER + "</script>")
    Summary summary(@Param("keyword") String keyword, @Param("memberType") String memberType,
                    @Param("dbEncString") String dbEncString);

    @Select("<script>SELECT m.seq, m.licenseNumber, "
            + "CONVERT(AES_DECRYPT(UNHEX(m.fullName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS fullName, "
            + "m.affiliation, m.memberType, m.updatedAt " + FILTER
            + " ORDER BY m.seq DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<Member> page(@Param("keyword") String keyword, @Param("memberType") String memberType,
                      @Param("size") int size, @Param("offset") int offset,
                      @Param("dbEncString") String dbEncString);

    @Select("SELECT seq, licenseNumber, CONVERT(AES_DECRYPT(UNHEX(fullName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS fullName, affiliation, memberType, createdAt, updatedAt FROM society_members WHERE seq = #{seq}")
    Member findBySeq(@Param("seq") Long seq, @Param("dbEncString") String dbEncString);

    @Select("SELECT seq, licenseNumber, CONVERT(AES_DECRYPT(UNHEX(fullName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS fullName, affiliation, memberType, createdAt, updatedAt FROM society_members WHERE licenseNumber = #{licenseNumber}")
    Member findByLicense(@Param("licenseNumber") String licenseNumber, @Param("dbEncString") String dbEncString);

    @Insert("""
            INSERT INTO society_members (licenseNumber, fullName, affiliation, memberType)
            VALUES (#{member.licenseNumber}, HEX(AES_ENCRYPT(#{member.fullName}, SHA2(#{dbEncString}, 512))),
                    #{member.affiliation}, #{member.memberType})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "member.seq")
    int insert(@Param("member") Member member, @Param("dbEncString") String dbEncString);

    @Update("""
            UPDATE society_members SET licenseNumber = #{member.licenseNumber},
                fullName = HEX(AES_ENCRYPT(#{member.fullName}, SHA2(#{dbEncString}, 512))),
                affiliation = #{member.affiliation}, memberType = #{member.memberType}, updatedAt = CURRENT_TIMESTAMP
            WHERE seq = #{member.seq}
            """)
    int update(@Param("member") Member member, @Param("dbEncString") String dbEncString);

    @Delete("DELETE FROM society_members WHERE seq = #{seq}")
    int delete(Long seq);

    @Select("SELECT memberType, categorySeq FROM society_member_fee_mappings WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} ORDER BY seq")
    List<FeeMapping> mappings(@Param("conferenceSeq") Long conferenceSeq);

    @Insert("""
            INSERT INTO society_member_fee_mappings (conferenceSeq, memberType, categorySeq, createdAt, updatedAt)
            VALUES (#{conferenceSeq,javaType=java.lang.Long}, #{mapping.memberType}, #{mapping.categorySeq}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE categorySeq = VALUES(categorySeq), updatedAt = CURRENT_TIMESTAMP
            """)
    void upsertMapping(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("mapping") FeeMapping mapping
    );

    @Select("""
            SELECT m.memberType, c.seq AS categorySeq, c.categoryName, r.amount
            FROM society_member_fee_mappings m
            JOIN registration_categories c ON c.seq = m.categorySeq AND c.conferenceSeq = m.conferenceSeq AND c.isUsed = 'Y' AND c.isDelete = 'N'
            JOIN registration_fee_rates r ON r.categorySeq = c.seq
                AND r.periodType = #{periodType} AND r.currency = #{currency}
            WHERE m.memberType = #{memberType}
              AND m.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    FeeQuote quote(@Param("conferenceSeq") Long conferenceSeq,
                   @Param("memberType") String memberType, @Param("periodType") String periodType,
                   @Param("currency") String currency);
}
