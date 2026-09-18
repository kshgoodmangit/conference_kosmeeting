package com.bjworld21.congress.repository;

import com.bjworld21.congress.dto.ReviewerReviewEvaluationItemResponse;
import com.bjworld21.congress.dto.ReviewerReviewListItemResponse;
import com.bjworld21.congress.entity.AbstractReview;
import com.bjworld21.congress.entity.AbstractReviewAssignment;
import com.bjworld21.congress.entity.AbstractReviewScore;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ReviewerReviewRepository {

    @Select("""
            SELECT
                assignment.seq AS assignmentSeq,
                submission.seq AS abstractSeq,
                submission.submissionNo,
                submission.title,
                category.codeName AS categoryName,
                presentationType.codeName AS presentationTypeName,
                assignment.status AS assignmentStatus,
                assignment.dueAt,
                assignment.assignedAt,
                review.seq AS reviewSeq,
                review.status AS reviewStatus,
                review.recommendation,
                review.updatedAt AS reviewUpdatedAt
            FROM abstract_review_assignments assignment
            JOIN abstract_submissions submission ON submission.seq = assignment.abstractSeq
                AND submission.conferenceSeq = assignment.conferenceSeq
            JOIN common_codes category ON category.seq = submission.categoryCode
            JOIN common_codes presentationType ON presentationType.seq = submission.presentationTypeCode
            LEFT JOIN abstract_reviews review ON review.assignmentSeq = assignment.seq
            WHERE assignment.reviewerSeq = #{reviewerSeq}
              AND assignment.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND assignment.status IN ('assigned', 'accepted', 'in_review', 'completed')
              AND (#{presentationTypeCode} IS NULL OR submission.presentationTypeCode = #{presentationTypeCode})
              AND (#{categoryCode} IS NULL OR submission.categoryCode = #{categoryCode})
              AND (#{status} IS NULL OR #{status} = '' OR assignment.status = #{status})
              AND (
                  #{keyword} IS NULL OR #{keyword} = ''
                  OR LOWER(submission.submissionNo) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                  OR LOWER(submission.title) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                  OR LOWER(category.codeName) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                  OR LOWER(presentationType.codeName) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                  OR LOWER(assignment.status) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              )
            ORDER BY assignment.status = 'completed' ASC,
                     assignment.dueAt IS NULL ASC,
                     assignment.dueAt ASC,
                     assignment.assignedAt DESC,
                     assignment.seq DESC
            """)
    List<ReviewerReviewListItemResponse> findAssignments(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("reviewerSeq") Long reviewerSeq,
            @Param("keyword") String keyword,
            @Param("presentationTypeCode") Long presentationTypeCode,
            @Param("categoryCode") Long categoryCode,
            @Param("status") String status
    );

    @Select("""
            SELECT *
            FROM abstract_review_assignments
            WHERE seq = #{assignmentSeq}
              AND reviewerSeq = #{reviewerSeq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND status IN ('assigned', 'accepted', 'in_review', 'completed')
            """)
    AbstractReviewAssignment findAssignmentForReviewer(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("assignmentSeq") Long assignmentSeq,
            @Param("reviewerSeq") Long reviewerSeq
    );

    @Select("""
            SELECT review.*
            FROM abstract_reviews review
            JOIN abstract_review_assignments assignment ON assignment.seq = review.assignmentSeq
            WHERE review.assignmentSeq = #{assignmentSeq}
              AND assignment.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    AbstractReview findReviewByAssignment(@Param("conferenceSeq") Long conferenceSeq,
                                          @Param("assignmentSeq") Long assignmentSeq);

    @Insert("""
            INSERT INTO abstract_reviews (
                assignmentSeq, status, recommendation, overallComment, confidentialComment,
                submittedAt, createdAt, updatedAt
            ) VALUES (
                #{assignmentSeq}, 'draft', #{recommendation}, #{overallComment}, #{confidentialComment},
                NULL, NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insertReview(AbstractReview review);

    @Update("""
            UPDATE abstract_reviews
            SET status = 'draft',
                recommendation = #{recommendation},
                overallComment = #{overallComment},
                confidentialComment = #{confidentialComment},
                submittedAt = NULL,
                updatedAt = NOW()
            WHERE seq = #{seq}
              AND status = 'draft'
            """)
    int updateDraft(AbstractReview review);

    @Update("""
            UPDATE abstract_reviews
            SET status = 'submitted',
                recommendation = #{recommendation},
                overallComment = #{overallComment},
                confidentialComment = #{confidentialComment},
                submittedAt = NOW(),
                updatedAt = NOW()
            WHERE seq = #{seq}
              AND status = 'draft'
            """)
    int submitReview(AbstractReview review);

    @Delete("DELETE FROM abstract_review_scores WHERE reviewSeq = #{reviewSeq}")
    void deleteScores(@Param("reviewSeq") Long reviewSeq);

    @Insert("""
            INSERT INTO abstract_review_scores (
                reviewSeq, evaluationItemSeq, score, itemComment, createdAt, updatedAt
            ) VALUES (
                #{reviewSeq}, #{evaluationItemSeq}, #{score}, #{itemComment}, NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insertScore(AbstractReviewScore score);

    @Select("""
            SELECT
                item.seq AS evaluationItemSeq,
                item.itemName,
                item.description,
                item.sortOrder,
                item.score1Guide,
                item.score2Guide,
                item.score3Guide,
                item.score4Guide,
                item.score5Guide,
                item.score6Guide,
                score.score,
                score.itemComment
            FROM abstract_evaluation_items item
            LEFT JOIN abstract_review_scores score
              ON score.evaluationItemSeq = item.seq
             AND score.reviewSeq = #{reviewSeq,jdbcType=BIGINT}
            WHERE ((item.isDelete = 'N' AND item.isUsed = 'Y')
               OR score.seq IS NOT NULL)
              AND item.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            ORDER BY item.sortOrder ASC, item.seq ASC
            """)
    List<ReviewerReviewEvaluationItemResponse> findEvaluationItems(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("reviewSeq") Long reviewSeq
    );

    @Update("""
            UPDATE abstract_review_assignments
            SET status = 'in_review',
                startedAt = COALESCE(startedAt, NOW()),
                updatedAt = NOW()
            WHERE seq = #{assignmentSeq}
              AND reviewerSeq = #{reviewerSeq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND status IN ('assigned', 'accepted', 'in_review')
            """)
    int markInReview(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("assignmentSeq") Long assignmentSeq,
            @Param("reviewerSeq") Long reviewerSeq
    );

    @Update("""
            UPDATE abstract_review_assignments
            SET status = 'completed',
                startedAt = COALESCE(startedAt, NOW()),
                completedAt = NOW(),
                updatedAt = NOW()
            WHERE seq = #{assignmentSeq}
              AND reviewerSeq = #{reviewerSeq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND status IN ('assigned', 'accepted', 'in_review')
            """)
    int markCompleted(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("assignmentSeq") Long assignmentSeq,
            @Param("reviewerSeq") Long reviewerSeq
    );
}
