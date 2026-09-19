package com.bjworld21.conference.repository;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface MemberEmailVerificationRepository {
    @Insert("""
            INSERT INTO member_email_verification_limits (keyHash, windowStart, requestCount)
            VALUES (#{hash}, #{now}, 0)
            ON DUPLICATE KEY UPDATE keyHash = VALUES(keyHash)
            """)
    void ensureLimit(@Param("hash") String hash, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE member_email_verification_limits
            SET requestCount = CASE WHEN windowStart <= #{cutoff} THEN 1 ELSE requestCount + 1 END,
                windowStart = CASE WHEN windowStart <= #{cutoff} THEN #{now} ELSE windowStart END
            WHERE keyHash = #{hash} AND (windowStart <= #{cutoff} OR requestCount < #{maximum})
            """)
    int takeLimit(@Param("hash") String hash, @Param("now") LocalDateTime now,
                  @Param("cutoff") LocalDateTime cutoff, @Param("maximum") int maximum);

    @Delete("DELETE FROM member_email_verification_limits WHERE windowStart < #{cutoff}")
    void purgeLimits(LocalDateTime cutoff);
}
