package com.bjworld21.conference.repository;

import com.bjworld21.conference.dto.ReviewerAssignmentCandidateResponse;
import com.bjworld21.conference.entity.AbstractReviewAssignment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AbstractReviewAssignmentRepository {

    @Select("""
            SELECT
                r.seq AS reviewerSeq,
                CONVERT(AES_DECRYPT(UNHEX(a.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS reviewerName,
                r.affiliation,
                r.department,
                r.positionTitle,
                CONVERT(AES_DECRYPT(UNHEX(r.contactEmail), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS contactEmail,
                (
                    SELECT GROUP_CONCAT(DISTINCT c.codeName ORDER BY c.sortOrder, c.seq SEPARATOR ', ')
                    FROM reviewer_expertise expertise
                    JOIN common_codes c ON c.seq = expertise.categoryCode
                    WHERE expertise.reviewerSeq = r.seq
                      AND c.isDelete = 'N'
                ) AS expertiseNames,
                EXISTS (
                    SELECT 1
                    FROM reviewer_expertise expertise
                    WHERE expertise.reviewerSeq = r.seq
                      AND expertise.categoryCode = s.categoryCode
                ) AS expertiseMatched,
                (
                    SELECT COUNT(*)
                    FROM abstract_review_assignments workload
                    WHERE workload.reviewerSeq = r.seq
                      AND workload.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
                      AND workload.status IN ('assigned', 'accepted', 'in_review')
                ) AS activeAssignmentCount,
                currentAssignment.seq AS assignmentSeq,
                currentAssignment.status AS assignmentStatus,
                currentAssignment.dueAt
            FROM abstract_submissions s
            CROSS JOIN reviewers r
            JOIN admin_accounts a
              ON a.seq = r.adminSeq
            LEFT JOIN abstract_review_assignments currentAssignment
              ON currentAssignment.abstractSeq = s.seq
             AND currentAssignment.reviewerSeq = r.seq
             AND currentAssignment.conferenceSeq = s.conferenceSeq
            WHERE s.seq = #{abstractSeq}
              AND s.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND r.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND (
                    (r.isUsed = 'Y' AND r.isDelete = 'N' AND a.role = 'reviewer' AND a.status = 'active')
                    OR currentAssignment.status IN ('assigned', 'accepted', 'in_review', 'completed')
              )
            ORDER BY CASE WHEN EXISTS (
                         SELECT 1
                         FROM reviewer_expertise sortExpertise
                         WHERE sortExpertise.reviewerSeq = r.seq
                           AND sortExpertise.categoryCode = s.categoryCode
                     ) THEN 0 ELSE 1 END,
                     activeAssignmentCount ASC,
                     CONVERT(AES_DECRYPT(UNHEX(a.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) ASC,
                     r.seq ASC
            """)
    List<ReviewerAssignmentCandidateResponse> findCandidates(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("abstractSeq") Long abstractSeq,
            @Param("dbEncString") String dbEncString
    );

    @Select("""
            SELECT COUNT(*)
            FROM reviewers r
            JOIN admin_accounts a ON a.seq = r.adminSeq
            WHERE r.seq = #{reviewerSeq}
              AND r.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND r.isUsed = 'Y'
              AND r.isDelete = 'N'
              AND a.role = 'reviewer'
              AND a.status = 'active'
            """)
    long countEligibleReviewer(@Param("conferenceSeq") Long conferenceSeq,
                               @Param("reviewerSeq") Long reviewerSeq);

    @Select("""
            SELECT r.seq
            FROM reviewers r
            JOIN admin_accounts a ON a.seq = r.adminSeq
            WHERE a.email = HEX(AES_ENCRYPT(LOWER(TRIM(#{reviewerId})), SHA2(#{dbEncString}, 512)))
              AND r.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND r.isUsed = 'Y'
              AND r.isDelete = 'N'
              AND a.role = 'reviewer'
              AND a.status = 'active'
            LIMIT 1
            """)
    Long findEligibleReviewerSeqById(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("reviewerId") String reviewerId,
            @Param("dbEncString") String dbEncString
    );

    @Select("""
            SELECT *
            FROM abstract_review_assignments
            WHERE abstractSeq = #{abstractSeq}
              AND reviewerSeq = #{reviewerSeq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    AbstractReviewAssignment findByAbstractAndReviewer(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("abstractSeq") Long abstractSeq,
            @Param("reviewerSeq") Long reviewerSeq
    );

    @Select("""
            SELECT reviewerSeq
            FROM abstract_review_assignments
            WHERE abstractSeq = #{abstractSeq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND status IN ('assigned', 'accepted', 'in_review')
            """)
    List<Long> findActiveReviewerSeqs(@Param("conferenceSeq") Long conferenceSeq,
                                      @Param("abstractSeq") Long abstractSeq);

    @Insert("""
            INSERT INTO abstract_review_assignments (
                conferenceSeq, abstractSeq, reviewerSeq, assignedByAdminSeq, status, dueAt,
                assignedAt, createdAt, updatedAt
            ) VALUES (
                #{conferenceSeq,javaType=java.lang.Long}, #{abstractSeq}, #{reviewerSeq}, #{assignedByAdminSeq}, 'assigned', #{dueAt},
                NOW(), NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insert(AbstractReviewAssignment assignment);

    @Update("""
            UPDATE abstract_review_assignments
            SET assignedByAdminSeq = #{assignedByAdminSeq},
                status = 'assigned',
                dueAt = #{dueAt},
                assignedAt = NOW(),
                acceptedAt = NULL,
                startedAt = NULL,
                completedAt = NULL,
                declinedAt = NULL,
                declineReason = NULL,
                cancelledAt = NULL,
                updatedAt = NOW()
            WHERE seq = #{seq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    void reactivate(AbstractReviewAssignment assignment);

    @Update("""
            UPDATE abstract_review_assignments
            SET dueAt = #{dueAt},
                updatedAt = NOW()
            WHERE seq = #{seq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND status IN ('assigned', 'accepted', 'in_review')
            """)
    void updateDueAt(@Param("conferenceSeq") Long conferenceSeq,
                     @Param("seq") Long seq,
                     @Param("dueAt") LocalDateTime dueAt);

    @Update("""
            UPDATE abstract_review_assignments
            SET status = 'cancelled',
                cancelledAt = NOW(),
                updatedAt = NOW()
            WHERE abstractSeq = #{abstractSeq}
              AND reviewerSeq = #{reviewerSeq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND status IN ('assigned', 'accepted', 'in_review')
            """)
    void cancel(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("abstractSeq") Long abstractSeq,
            @Param("reviewerSeq") Long reviewerSeq
    );

    @Select("""
            SELECT COUNT(*)
            FROM abstract_review_assignments
            WHERE abstractSeq = #{abstractSeq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND status IN ('assigned', 'accepted', 'in_review', 'completed')
            """)
    long countEffectiveAssignments(@Param("conferenceSeq") Long conferenceSeq,
                                   @Param("abstractSeq") Long abstractSeq);

    @Select("""
            SELECT COUNT(*)
            FROM abstract_review_assignments assignment
            JOIN abstract_reviews review
              ON review.assignmentSeq = assignment.seq
             AND review.status = 'submitted'
            WHERE assignment.abstractSeq = #{abstractSeq}
              AND assignment.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND assignment.status = 'completed'
            """)
    long countCompletedSubmittedReviews(@Param("conferenceSeq") Long conferenceSeq,
                                        @Param("abstractSeq") Long abstractSeq);

    @Delete("DELETE FROM abstract_review_assignments WHERE abstractSeq = #{abstractSeq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    void deleteByAbstractSeq(@Param("conferenceSeq") Long conferenceSeq,
                             @Param("abstractSeq") Long abstractSeq);
}
