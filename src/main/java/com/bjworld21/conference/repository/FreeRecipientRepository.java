package com.bjworld21.conference.repository;

import com.bjworld21.conference.dto.FreeRecipientData.*;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface FreeRecipientRepository {
    String FILTER = """
            FROM free_recipients r
            <where>
                AND r.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
                <if test="keyword != ''">
                    (LOCATE(#{keyword}, CONVERT(AES_DECRYPT(UNHEX(r.fullName),SHA2(#{dbEncString},512)) USING utf8mb4)) > 0
                    OR LOCATE(#{keyword}, r.affiliation) > 0 OR LOCATE(#{keyword}, r.position) > 0
                    OR LOCATE(#{keyword}, CONVERT(AES_DECRYPT(UNHEX(r.email),SHA2(#{dbEncString},512)) USING utf8mb4)) > 0
                    OR LOCATE(#{keyword}, CONVERT(AES_DECRYPT(UNHEX(r.phoneNumber),SHA2(#{dbEncString},512)) USING utf8mb4)) > 0
                    <if test="phoneKeyword != ''">OR LOCATE(#{phoneKeyword}, CONVERT(AES_DECRYPT(UNHEX(r.normalizedPhone),SHA2(#{dbEncString},512)) USING utf8mb4)) > 0</if>)
                </if>
                <if test="recipientType != ''">AND r.recipientType = #{recipientType}</if>
                <if test="isUsed != ''">AND r.isUsed = #{isUsed}</if>
            </where>
            """;
    @Select("<script>SELECT COUNT(*) AS totalCount, COALESCE(SUM(isUsed = 'Y'), 0) AS activeCount, "
            + "COALESCE(SUM(isUsed = 'N'), 0) AS inactiveCount " + FILTER + "</script>")
    Summary summary(@Param("conferenceSeq") Long conferenceSeq,
                    @Param("keyword") String keyword, @Param("phoneKeyword") String phoneKeyword,
                    @Param("recipientType") String recipientType, @Param("isUsed") String isUsed,
                    @Param("dbEncString") String dbEncString);
    @Select("<script>SELECT r.seq,r.affiliation,"
            + "CONVERT(AES_DECRYPT(UNHEX(r.fullName),SHA2(#{dbEncString},512)) USING utf8mb4) AS fullName,"
            + "r.position,CONVERT(AES_DECRYPT(UNHEX(r.phoneNumber),SHA2(#{dbEncString},512)) USING utf8mb4) AS phoneNumber,"
            + "CONVERT(AES_DECRYPT(UNHEX(r.normalizedPhone),SHA2(#{dbEncString},512)) USING utf8mb4) AS normalizedPhone,"
            + "CONVERT(AES_DECRYPT(UNHEX(r.email),SHA2(#{dbEncString},512)) USING utf8mb4) AS email,"
            + "r.recipientType,r.isUsed,r.adminMemo,r.createdAt,r.updatedAt " + FILTER
            + " ORDER BY r.seq DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<Recipient> page(@Param("conferenceSeq") Long conferenceSeq,
                         @Param("keyword") String keyword, @Param("phoneKeyword") String phoneKeyword,
                         @Param("recipientType") String recipientType, @Param("isUsed") String isUsed,
                         @Param("size") int size, @Param("offset") int offset,
                         @Param("dbEncString") String dbEncString);
    @Select("SELECT seq,affiliation,CONVERT(AES_DECRYPT(UNHEX(fullName),SHA2(#{dbEncString},512)) USING utf8mb4) AS fullName,position,CONVERT(AES_DECRYPT(UNHEX(phoneNumber),SHA2(#{dbEncString},512)) USING utf8mb4) AS phoneNumber,CONVERT(AES_DECRYPT(UNHEX(normalizedPhone),SHA2(#{dbEncString},512)) USING utf8mb4) AS normalizedPhone,CONVERT(AES_DECRYPT(UNHEX(email),SHA2(#{dbEncString},512)) USING utf8mb4) AS email,recipientType,isUsed,adminMemo,createdAt,updatedAt FROM free_recipients WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    Recipient findBySeq(@Param("conferenceSeq") Long conferenceSeq,
                        @Param("seq") Long seq, @Param("dbEncString") String dbEncString);
    @Insert("""
            INSERT INTO free_recipients (conferenceSeq, affiliation, fullName, position, phoneNumber, normalizedPhone,
                email, recipientType, isUsed, adminMemo)
            VALUES (#{conferenceSeq,javaType=java.lang.Long},#{recipient.affiliation},HEX(AES_ENCRYPT(#{recipient.fullName},SHA2(#{dbEncString},512))),
                #{recipient.position},HEX(AES_ENCRYPT(#{recipient.phoneNumber},SHA2(#{dbEncString},512))),
                HEX(AES_ENCRYPT(#{recipient.normalizedPhone},SHA2(#{dbEncString},512))),
                HEX(AES_ENCRYPT(#{recipient.email},SHA2(#{dbEncString},512))),
                #{recipient.recipientType},#{recipient.isUsed},#{recipient.adminMemo})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "recipient.seq")
    int insert(@Param("conferenceSeq") Long conferenceSeq,
               @Param("recipient") Recipient recipient, @Param("dbEncString") String dbEncString);
    @Update("""
            UPDATE free_recipients SET affiliation=#{recipient.affiliation},
                fullName=HEX(AES_ENCRYPT(#{recipient.fullName},SHA2(#{dbEncString},512))),
                position=#{recipient.position},
                phoneNumber=HEX(AES_ENCRYPT(#{recipient.phoneNumber},SHA2(#{dbEncString},512))),
                normalizedPhone=HEX(AES_ENCRYPT(#{recipient.normalizedPhone},SHA2(#{dbEncString},512))),
                email=HEX(AES_ENCRYPT(#{recipient.email},SHA2(#{dbEncString},512))),
                recipientType=#{recipient.recipientType},isUsed=#{recipient.isUsed},adminMemo=#{recipient.adminMemo},
                updatedAt=CURRENT_TIMESTAMP WHERE seq=#{recipient.seq} AND conferenceSeq=#{conferenceSeq,javaType=java.lang.Long}
            """)
    int update(@Param("conferenceSeq") Long conferenceSeq,
               @Param("recipient") Recipient recipient, @Param("dbEncString") String dbEncString);
    @Delete("DELETE FROM free_recipients WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    int delete(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);
}
