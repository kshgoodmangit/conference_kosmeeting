package com.bjworld21.conference.repository;

import com.bjworld21.conference.dto.AbstractSimilarityReviewHistoryResponse;
import com.bjworld21.conference.entity.AbstractSimilarityReview;
import com.bjworld21.conference.entity.AbstractSimilarityReviewCandidate;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AbstractSimilarityReviewRepository {

    @Select("""
            SELECT source.seq AS abstractSeq,
                   source.submissionNo,
                   source.title,
                   source.status AS abstractStatus,
                   target.seq AS targetAbstractSeq,
                   target.submissionNo AS targetSubmissionNo,
                   target.title AS targetTitle,
                   ranked.overallSimilarity,
                   ranked.highestSection,
                   ranked.highestSimilarity,
                   ranked.analyzedAt,
                   CASE
                       WHEN source.updatedAt > ranked.analyzedAt OR target.updatedAt > ranked.analyzedAt THEN TRUE
                       ELSE FALSE
                   END AS stale,
                   review.seq AS reviewSeq,
                   review.status AS reviewStatus,
                   review.reviewOpinion,
                   review.rejectionRecommended,
                   review.handledByAdminSeq,
                   CONVERT(AES_DECRYPT(UNHEX(admin.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4)
                       AS handledByAdminName,
                   review.handledAt,
                   review.updatedAt
            FROM (
                SELECT result.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY result.sourceAbstractSeq
                           ORDER BY result.overallSimilarity DESC,
                                    result.highestSimilarity DESC,
                                    result.targetAbstractSeq ASC
                       ) AS candidateRank
                FROM abstract_similarity_results result
                JOIN abstract_submissions scopedSource ON scopedSource.seq = result.sourceAbstractSeq
                JOIN abstract_submissions scopedTarget ON scopedTarget.seq = result.targetAbstractSeq
                WHERE scopedSource.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
                  AND scopedTarget.conferenceSeq = scopedSource.conferenceSeq
                  AND scopedSource.status <> 'draft'
                  AND scopedTarget.status <> 'draft'
                  AND (
                      result.overallSimilarity >= #{overallThreshold}
                      OR result.highestSimilarity >= #{sectionThreshold}
                  )
            ) ranked
            JOIN abstract_submissions source ON source.seq = ranked.sourceAbstractSeq
            JOIN abstract_submissions target ON target.seq = ranked.targetAbstractSeq
            LEFT JOIN abstract_similarity_reviews review
              ON review.conferenceSeq = source.conferenceSeq
             AND review.abstractSeq = source.seq
            LEFT JOIN admin_accounts admin ON admin.seq = review.handledByAdminSeq
            WHERE ranked.candidateRank = 1
            ORDER BY
                CASE COALESCE(review.status, 'PENDING')
                    WHEN 'VIOLATION_CONFIRMED' THEN 0
                    WHEN 'VIOLATION_SUSPECTED' THEN 1
                    WHEN 'EXPLANATION_RECEIVED' THEN 2
                    WHEN 'EXPLANATION_REQUESTED' THEN 3
                    WHEN 'IN_REVIEW' THEN 4
                    WHEN 'PENDING' THEN 5
                    ELSE 6
                END,
                ranked.overallSimilarity DESC,
                ranked.highestSimilarity DESC,
                source.seq DESC
            """)
    List<AbstractSimilarityReviewCandidate> findCandidates(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("overallThreshold") double overallThreshold,
            @Param("sectionThreshold") double sectionThreshold,
            @Param("dbEncString") String dbEncString
    );

    @Select("""
            SELECT seq, conferenceSeq, abstractSeq, status, reviewOpinion,
                   rejectionRecommended, handledByAdminSeq, handledAt, createdAt, updatedAt
            FROM abstract_similarity_reviews
            WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND abstractSeq = #{abstractSeq}
            """)
    AbstractSimilarityReview findReview(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("abstractSeq") Long abstractSeq
    );

    @Insert("""
            INSERT INTO abstract_similarity_reviews (
                conferenceSeq, abstractSeq, status, reviewOpinion,
                rejectionRecommended, handledByAdminSeq, handledAt, createdAt, updatedAt
            ) VALUES (
                #{review.conferenceSeq,javaType=java.lang.Long}, #{review.abstractSeq}, #{review.status},
                #{review.reviewOpinion}, #{review.rejectionRecommended},
                #{review.handledByAdminSeq}, NOW(), NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "review.seq")
    int insertReview(@Param("review") AbstractSimilarityReview review);

    @Update("""
            UPDATE abstract_similarity_reviews
            SET status = #{review.status},
                reviewOpinion = #{review.reviewOpinion},
                rejectionRecommended = #{review.rejectionRecommended},
                handledByAdminSeq = #{review.handledByAdminSeq},
                handledAt = NOW(),
                updatedAt = NOW()
            WHERE seq = #{review.seq}
              AND conferenceSeq = #{review.conferenceSeq,javaType=java.lang.Long}
              AND abstractSeq = #{review.abstractSeq}
            """)
    int updateReview(@Param("review") AbstractSimilarityReview review);

    @Insert("""
            INSERT INTO abstract_similarity_review_histories (
                reviewSeq, conferenceSeq, abstractSeq, previousStatus, status,
                reviewOpinion, rejectionRecommended, handledByAdminSeq, handledAt, createdAt
            ) VALUES (
                #{reviewSeq}, #{conferenceSeq,javaType=java.lang.Long}, #{abstractSeq},
                #{previousStatus}, #{status}, #{reviewOpinion}, #{rejectionRecommended},
                #{handledByAdminSeq}, NOW(), NOW()
            )
            """)
    int insertHistory(
            @Param("reviewSeq") Long reviewSeq,
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("abstractSeq") Long abstractSeq,
            @Param("previousStatus") String previousStatus,
            @Param("status") String status,
            @Param("reviewOpinion") String reviewOpinion,
            @Param("rejectionRecommended") boolean rejectionRecommended,
            @Param("handledByAdminSeq") Long handledByAdminSeq
    );

    @Select("""
            SELECT history.seq,
                   history.previousStatus,
                   history.status,
                   history.reviewOpinion,
                   history.rejectionRecommended,
                   history.handledByAdminSeq,
                   CONVERT(AES_DECRYPT(UNHEX(admin.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4)
                       AS handledByAdminName,
                   history.handledAt
            FROM abstract_similarity_review_histories history
            JOIN abstract_similarity_reviews review ON review.seq = history.reviewSeq
            LEFT JOIN admin_accounts admin ON admin.seq = history.handledByAdminSeq
            WHERE history.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND history.abstractSeq = #{abstractSeq}
              AND review.conferenceSeq = history.conferenceSeq
              AND review.abstractSeq = history.abstractSeq
            ORDER BY history.handledAt DESC, history.seq DESC
            """)
    List<AbstractSimilarityReviewHistoryResponse> findHistory(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("abstractSeq") Long abstractSeq,
            @Param("dbEncString") String dbEncString
    );
}
