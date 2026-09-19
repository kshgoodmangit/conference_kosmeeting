package com.bjworld21.conference.repository;

import com.bjworld21.conference.entity.Member;
import com.bjworld21.conference.entity.MemberPasswordResetToken;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface MemberPasswordResetRepository {
    @Select("""
            SELECT seq, password FROM members
            WHERE conferenceSeq = #{conferenceSeq}
              AND email = HEX(AES_ENCRYPT(#{email}, SHA2(#{key}, 512)))
            FOR UPDATE
            """)
    Member lockMemberByEmail(@Param("conferenceSeq") long conferenceSeq,
                             @Param("email") String email, @Param("key") String key);

    @Select("SELECT password FROM members WHERE conferenceSeq = #{conferenceSeq} AND seq = #{memberSeq} FOR UPDATE")
    String lockCredential(@Param("conferenceSeq") long conferenceSeq, @Param("memberSeq") long memberSeq);

    @Insert("""
            INSERT INTO member_password_reset_tokens
                (tokenHash, conferenceSeq, memberSeq, credentialFingerprint, expiresAt, createdAt)
            VALUES (#{tokenHash}, #{conferenceSeq}, #{memberSeq}, #{credentialFingerprint}, #{expiresAt}, UTC_TIMESTAMP(6))
            """)
    void insert(MemberPasswordResetToken token);

    @Select("""
            SELECT tokenHash, conferenceSeq, memberSeq, credentialFingerprint, expiresAt, usedAt
            FROM member_password_reset_tokens WHERE tokenHash = #{hash} AND conferenceSeq = #{conferenceSeq}
            """)
    MemberPasswordResetToken find(@Param("conferenceSeq") long conferenceSeq, @Param("hash") String hash);

    @Select("""
            SELECT tokenHash, conferenceSeq, memberSeq, credentialFingerprint, expiresAt, usedAt
            FROM member_password_reset_tokens WHERE tokenHash = #{hash} AND conferenceSeq = #{conferenceSeq} FOR UPDATE
            """)
    MemberPasswordResetToken lockToken(@Param("conferenceSeq") long conferenceSeq, @Param("hash") String hash);

    @Update("""
            UPDATE members SET password = #{password}, updatedAt = NOW()
            WHERE conferenceSeq = #{conferenceSeq} AND seq = #{memberSeq}
            """)
    int updatePassword(@Param("conferenceSeq") long conferenceSeq, @Param("memberSeq") long memberSeq,
                       @Param("password") String password);

    @Update("""
            UPDATE member_password_reset_tokens SET usedAt = #{now}
            WHERE conferenceSeq = #{conferenceSeq} AND memberSeq = #{memberSeq} AND usedAt IS NULL
            """)
    void consumeAll(@Param("conferenceSeq") long conferenceSeq, @Param("memberSeq") long memberSeq,
                    @Param("now") LocalDateTime now);

    @Delete("DELETE FROM member_password_reset_tokens WHERE tokenHash = #{hash}")
    void deleteToken(String hash);

    @Insert("""
            INSERT INTO member_password_reset_limits (keyHash, windowStart, requestCount)
            VALUES (#{hash}, #{windowStart}, 0)
            ON DUPLICATE KEY UPDATE keyHash = VALUES(keyHash)
            """)
    void ensureLimit(@Param("hash") String hash, @Param("windowStart") LocalDateTime windowStart);

    @Update("""
            UPDATE member_password_reset_limits SET requestCount = requestCount + 1
            WHERE keyHash = #{hash} AND windowStart = #{windowStart} AND requestCount < #{maximum}
            """)
    int takeLimit(@Param("hash") String hash, @Param("windowStart") LocalDateTime windowStart,
                  @Param("maximum") int maximum);

    @Delete("DELETE FROM member_password_reset_tokens WHERE expiresAt < #{cutoff}")
    void purgeTokens(LocalDateTime cutoff);

    @Delete("DELETE FROM member_password_reset_limits WHERE windowStart < #{cutoff}")
    void purgeLimits(LocalDateTime cutoff);
}
