package com.bjworld21.conference.repository;

import com.bjworld21.conference.dto.AbstractReviewReviewerResponse;
import com.bjworld21.conference.dto.AbstractReviewScoreResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminAbstractReviewResultRepository {

    @Select("""
            SELECT
                assignment.seq AS assignmentSeq,
                reviewer.seq AS reviewerSeq,
                CONVERT(AES_DECRYPT(UNHEX(account.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS reviewerName,
                reviewer.affiliation,
                reviewer.department,
                assignment.status AS assignmentStatus,
                assignment.dueAt,
                review.seq AS reviewSeq,
                review.status AS reviewStatus,
                CASE WHEN review.status = 'submitted' THEN review.recommendation END AS recommendation,
                CASE WHEN review.status = 'submitted' THEN review.overallComment END AS overallComment,
                CASE WHEN review.status = 'submitted' THEN review.confidentialComment END AS confidentialComment,
                CASE WHEN review.status = 'submitted' THEN review.submittedAt END AS submittedAt
            FROM abstract_review_assignments assignment
            JOIN reviewers reviewer ON reviewer.seq = assignment.reviewerSeq
            JOIN admin_accounts account ON account.seq = reviewer.adminSeq
            LEFT JOIN abstract_reviews review ON review.assignmentSeq = assignment.seq
            WHERE assignment.abstractSeq = #{abstractSeq}
              AND assignment.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND reviewer.conferenceSeq = assignment.conferenceSeq
              AND assignment.status IN ('assigned', 'accepted', 'in_review', 'completed')
            ORDER BY CASE WHEN review.status = 'submitted' THEN 0 ELSE 1 END,
                     review.submittedAt DESC,
                     CONVERT(AES_DECRYPT(UNHEX(account.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) ASC,
                     assignment.seq ASC
            """)
    List<AbstractReviewReviewerResponse> findReviews(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("abstractSeq") Long abstractSeq,
            @Param("dbEncString") String dbEncString
    );

    @Select("""
            SELECT
                review.seq AS reviewSeq,
                item.seq AS evaluationItemSeq,
                item.itemName,
                item.sortOrder,
                score.score,
                score.itemComment
            FROM abstract_review_assignments assignment
            JOIN abstract_reviews review
              ON review.assignmentSeq = assignment.seq
             AND review.status = 'submitted'
            JOIN abstract_review_scores score ON score.reviewSeq = review.seq
            JOIN abstract_evaluation_items item ON item.seq = score.evaluationItemSeq
            WHERE assignment.abstractSeq = #{abstractSeq}
              AND assignment.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND item.conferenceSeq = assignment.conferenceSeq
              AND assignment.status = 'completed'
            ORDER BY item.sortOrder ASC, item.seq ASC, review.seq ASC
            """)
    List<AbstractReviewScoreResponse> findSubmittedScores(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("abstractSeq") Long abstractSeq
    );
}
