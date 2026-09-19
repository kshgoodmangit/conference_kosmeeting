package com.bjworld21.conference.repository;

import com.bjworld21.conference.entity.AbstractTitleSimilarityCandidate;
import com.bjworld21.conference.entity.AbstractTitleSimilarityResult;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AbstractTitleSimilarityRepository {

    @Select("""
            SELECT seq, submissionNo, title, status
            FROM abstract_submissions
            WHERE seq = #{abstractSeq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    AbstractTitleSimilarityCandidate findSource(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("abstractSeq") Long abstractSeq
    );

    @Select("""
            SELECT seq, submissionNo, title, status
            FROM abstract_submissions
            WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND seq <> #{abstractSeq}
              AND status <> 'draft'
            ORDER BY seq
            """)
    List<AbstractTitleSimilarityCandidate> findCandidates(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("abstractSeq") Long abstractSeq
    );

    @Insert("""
            INSERT INTO abstract_title_similarity_checks (
                conferenceSeq, abstractSeq, maxSimilarity, matchCount,
                algorithmVersion, checkedAt, createdAt, updatedAt
            ) VALUES (
                #{conferenceSeq}, #{abstractSeq}, #{maxSimilarity}, #{matchCount},
                #{algorithmVersion}, #{checkedAt}, NOW(), NOW()
            )
            ON DUPLICATE KEY UPDATE
                maxSimilarity = VALUES(maxSimilarity),
                matchCount = VALUES(matchCount),
                algorithmVersion = VALUES(algorithmVersion),
                checkedAt = VALUES(checkedAt),
                updatedAt = NOW()
            """)
    void upsertCheck(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("abstractSeq") Long abstractSeq,
            @Param("maxSimilarity") double maxSimilarity,
            @Param("matchCount") int matchCount,
            @Param("algorithmVersion") String algorithmVersion,
            @Param("checkedAt") LocalDateTime checkedAt
    );

    @Select("""
            SELECT seq
            FROM abstract_title_similarity_checks
            WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND abstractSeq = #{abstractSeq}
            """)
    Long findCheckSeq(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("abstractSeq") Long abstractSeq
    );

    @Delete("DELETE FROM abstract_title_similarity_results WHERE sourceAbstractSeq = #{abstractSeq}")
    void deleteResults(@Param("abstractSeq") Long abstractSeq);

    @Insert("""
            INSERT INTO abstract_title_similarity_results (
                checkSeq, sourceAbstractSeq, targetAbstractSeq,
                targetSubmissionNoSnapshot, targetTitleSnapshot,
                similarityScore, levenshteinSimilarity, trigramSimilarity,
                jaccardSimilarity, exactMatch, checkedAt, createdAt
            ) VALUES (
                #{checkSeq}, #{sourceAbstractSeq}, #{targetAbstractSeq},
                #{targetSubmissionNo}, #{targetTitle},
                #{similarityScore}, #{levenshteinSimilarity}, #{trigramSimilarity},
                #{jaccardSimilarity}, #{exactMatch}, #{checkedAt}, NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insertResult(AbstractTitleSimilarityResult result);

    @Select("""
            SELECT result.seq,
                   result.checkSeq,
                   result.sourceAbstractSeq,
                   result.targetAbstractSeq,
                   result.targetSubmissionNoSnapshot AS targetSubmissionNo,
                   result.targetTitleSnapshot AS targetTitle,
                   result.similarityScore,
                   result.levenshteinSimilarity,
                   result.trigramSimilarity,
                   result.jaccardSimilarity,
                   result.exactMatch,
                   result.checkedAt
            FROM abstract_title_similarity_results result
            JOIN abstract_title_similarity_checks checkRow ON checkRow.seq = result.checkSeq
            WHERE checkRow.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND result.sourceAbstractSeq = #{abstractSeq}
            ORDER BY result.similarityScore DESC, result.targetAbstractSeq
            """)
    List<AbstractTitleSimilarityResult> findResults(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("abstractSeq") Long abstractSeq
    );
}
