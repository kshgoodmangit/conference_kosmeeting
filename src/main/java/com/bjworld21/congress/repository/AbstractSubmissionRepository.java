package com.bjworld21.congress.repository;

import com.bjworld21.congress.dto.AbstractCategoryResponse;
import com.bjworld21.congress.dto.AbstractAiOptionResponse;
import com.bjworld21.congress.dto.AbstractPresentationTypeResponse;
import com.bjworld21.congress.dto.AbstractSubmissionAiScope;
import com.bjworld21.congress.dto.AbstractSubmissionAiTool;
import com.bjworld21.congress.dto.AbstractSubmissionAuthor;
import com.bjworld21.congress.dto.AbstractSubmissionInstitution;
import com.bjworld21.congress.dto.AbstractSubmissionRequest;
import com.bjworld21.congress.dto.AbstractSubmissionResponse;
import com.bjworld21.congress.dto.AbstractSubmissionSummary;
import com.bjworld21.congress.dto.AbstractSubmissionAttachmentResponse;
import com.bjworld21.congress.dto.MemberAbstractSubmissionResponse;
import com.bjworld21.congress.entity.AbstractSubmissionAttachment;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AbstractSubmissionRepository {

    String LIST_FILTER_SQL = """
            FROM abstract_submissions s
            JOIN members m ON m.seq = s.memberSeq AND m.conferenceSeq = s.conferenceSeq
            JOIN common_codes pt
              ON pt.seq = s.presentationTypeCode
             AND pt.groupCode = 'ABSTRACT_PRESENTATION_TYPES'
             AND pt.isDelete = 'N'
            LEFT JOIN common_codes acceptedPt
              ON acceptedPt.seq = s.acceptedPresentationTypeCode
             AND acceptedPt.groupCode = 'ABSTRACT_PRESENTATION_TYPES'
             AND acceptedPt.isDelete = 'N'
            JOIN common_codes c
              ON c.seq = s.categoryCode
             AND c.groupCode = 'ABSTRACT_CATEGORY'
             AND c.isDelete = 'N'
            WHERE s.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND (
                #{keyword} IS NULL OR #{keyword} = ''
                OR LOWER(s.submissionNo) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(s.title) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(
            """ + MemberPersonalDataSql.EMAIL + """
                ) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(CONCAT(
            """ + MemberPersonalDataSql.FIRST_NAME + """
                    , ' ',
            """ + MemberPersonalDataSql.LAST_NAME + """
                )) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(pt.codeName) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(acceptedPt.codeName) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(c.codeName) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(s.status) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
            )
              AND (#{presentationTypeCode} IS NULL OR s.presentationTypeCode = #{presentationTypeCode})
              AND (#{acceptedPresentationTypeCode} IS NULL OR s.acceptedPresentationTypeCode = #{acceptedPresentationTypeCode})
              AND (#{categoryCode} IS NULL OR s.categoryCode = #{categoryCode})
              AND (#{status} IS NULL OR #{status} = '' OR s.status = #{status})
            """;

    @Select("SELECT COUNT(*) " + LIST_FILTER_SQL)
    long countByKeyword(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("keyword") String keyword,
            @Param("presentationTypeCode") Long presentationTypeCode,
            @Param("acceptedPresentationTypeCode") Long acceptedPresentationTypeCode,
            @Param("categoryCode") Long categoryCode,
            @Param("status") String status,
            @Param("dbEncString") String dbEncString
    );

    @Select("""
            SELECT
                COALESCE(SUM(status IN ('submitted', 'under_review') AND assignedCount = 0), 0) AS unassignedCount,
                COALESCE(SUM(status IN ('submitted', 'under_review') AND assignedCount > 0 AND pendingCount > 0), 0) AS pendingReviewCount,
                COALESCE(SUM(status IN ('submitted', 'under_review') AND assignedCount > 0 AND pendingCount = 0), 0) AS pendingDecisionCount,
                COALESCE(SUM(status = 'approved'), 0) AS acceptedCount
            FROM (
                SELECT s.status,
                    (SELECT COUNT(*) FROM abstract_review_assignments a
                     WHERE a.conferenceSeq = s.conferenceSeq AND a.abstractSeq = s.seq
                       AND a.status IN ('assigned', 'accepted', 'in_review', 'completed')) AS assignedCount,
                    (SELECT COUNT(*) FROM abstract_review_assignments a
                     WHERE a.conferenceSeq = s.conferenceSeq AND a.abstractSeq = s.seq
                       AND a.status IN ('assigned', 'accepted', 'in_review', 'completed')
                       AND (a.status <> 'completed' OR NOT EXISTS (
                           SELECT 1 FROM abstract_reviews r
                           WHERE r.assignmentSeq = a.seq AND r.status = 'submitted'
                       ))) AS pendingCount
            """ + LIST_FILTER_SQL + " ) summaryRows")
    AbstractSubmissionSummary summarizeByKeyword(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("keyword") String keyword,
            @Param("presentationTypeCode") Long presentationTypeCode,
            @Param("acceptedPresentationTypeCode") Long acceptedPresentationTypeCode,
            @Param("categoryCode") Long categoryCode,
            @Param("status") String status,
            @Param("dbEncString") String dbEncString
    );

    @Select("""
            SELECT
                s.seq,
                s.memberSeq,
                s.submissionSource,
                s.createdByAdminSeq,
                CONVERT(AES_DECRYPT(UNHEX(creator.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS createdByAdminName,
            """ + MemberPersonalDataSql.EMAIL + """
                AS memberEmail,
                CONCAT(
            """ + MemberPersonalDataSql.FIRST_NAME + """
                    , ' ',
            """ + MemberPersonalDataSql.LAST_NAME + """
                ) AS memberFullName,
                s.submissionNo,
                s.presentationTypeCode,
                pt.codeName AS presentationTypeName,
                s.acceptedPresentationTypeCode,
                acceptedPt.codeName AS acceptedPresentationTypeName,
                s.categoryCode,
                c.codeName AS categoryName,
                s.title,
                s.objectiveText,
                s.methodsText,
                s.resultsText,
                s.conclusionsText,
                s.aiUsage,
                s.aiVersionInfo,
                s.aiDataAnalysisUsed,
                s.plagiarismPolicyConfirmed,
                s.wordCount,
                s.status,
                s.decisionByAdminSeq,
                CONVERT(AES_DECRYPT(UNHEX(decisionAdmin.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS decisionByAdminName,
                s.decisionAt,
                s.decisionReason,
                s.forcedDecision,
                (
                    SELECT COUNT(*) FROM abstract_submission_authors a
                    WHERE a.abstractSeq = s.seq
                ) AS authorCount,
                (
                    SELECT COUNT(*) FROM abstract_submission_institutions i
                    WHERE i.abstractSeq = s.seq
                ) AS institutionCount,
                (
                    SELECT COUNT(*) FROM abstract_review_assignments assignment
                    WHERE assignment.abstractSeq = s.seq
                      AND assignment.conferenceSeq = s.conferenceSeq
                      AND assignment.status IN ('assigned', 'accepted', 'in_review', 'completed')
                ) AS reviewerAssignmentCount,
                (
                    SELECT COUNT(*)
                    FROM abstract_review_assignments completedAssignment
                    JOIN abstract_reviews completedReview
                      ON completedReview.assignmentSeq = completedAssignment.seq
                     AND completedReview.status = 'submitted'
                    WHERE completedAssignment.abstractSeq = s.seq
                      AND completedAssignment.conferenceSeq = s.conferenceSeq
                      AND completedAssignment.status = 'completed'
                ) AS completedReviewCount,
                (
                    SELECT ROUND(AVG(reviewScore.score), 2)
                    FROM abstract_review_assignments scoreAssignment
                    JOIN abstract_reviews scoreReview
                      ON scoreReview.assignmentSeq = scoreAssignment.seq
                     AND scoreReview.status = 'submitted'
                    JOIN abstract_review_scores reviewScore ON reviewScore.reviewSeq = scoreReview.seq
                    WHERE scoreAssignment.abstractSeq = s.seq
                      AND scoreAssignment.conferenceSeq = s.conferenceSeq
                      AND scoreAssignment.status = 'completed'
                ) AS averageReviewScore,
                (
                    SELECT titleCheck.maxSimilarity
                    FROM abstract_title_similarity_checks titleCheck
                    WHERE titleCheck.conferenceSeq = s.conferenceSeq
                      AND titleCheck.abstractSeq = s.seq
                ) AS titleSimilarityMaxScore,
                (
                    SELECT titleCheck.matchCount
                    FROM abstract_title_similarity_checks titleCheck
                    WHERE titleCheck.conferenceSeq = s.conferenceSeq
                      AND titleCheck.abstractSeq = s.seq
                ) AS titleSimilarityMatchCount,
                (
                    SELECT titleCheck.algorithmVersion
                    FROM abstract_title_similarity_checks titleCheck
                    WHERE titleCheck.conferenceSeq = s.conferenceSeq
                      AND titleCheck.abstractSeq = s.seq
                ) AS titleSimilarityAlgorithmVersion,
                (
                    SELECT titleCheck.checkedAt
                    FROM abstract_title_similarity_checks titleCheck
                    WHERE titleCheck.conferenceSeq = s.conferenceSeq
                      AND titleCheck.abstractSeq = s.seq
                ) AS titleSimilarityCheckedAt,
                (
                    SELECT CONVERT(AES_DECRYPT(UNHEX(a.authorName), SHA2(#{dbEncString}, 512)) USING utf8mb4)
                    FROM abstract_submission_authors a
                    WHERE a.abstractSeq = s.seq
                    ORDER BY a.authorOrder
                    LIMIT 1
                ) AS mainAuthorName,
                s.submittedAt,
                s.reviewedAt,
                s.createdAt,
                s.updatedAt
            FROM abstract_submissions s
            JOIN members m ON m.seq = s.memberSeq AND m.conferenceSeq = s.conferenceSeq
            JOIN common_codes pt
              ON pt.seq = s.presentationTypeCode
             AND pt.groupCode = 'ABSTRACT_PRESENTATION_TYPES'
             AND pt.isDelete = 'N'
            LEFT JOIN common_codes acceptedPt
              ON acceptedPt.seq = s.acceptedPresentationTypeCode
             AND acceptedPt.groupCode = 'ABSTRACT_PRESENTATION_TYPES'
             AND acceptedPt.isDelete = 'N'
            LEFT JOIN admin_accounts creator ON creator.seq = s.createdByAdminSeq
            LEFT JOIN admin_accounts decisionAdmin ON decisionAdmin.seq = s.decisionByAdminSeq
            JOIN common_codes c
              ON c.seq = s.categoryCode
             AND c.groupCode = 'ABSTRACT_CATEGORY'
             AND c.isDelete = 'N'
            WHERE s.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND (
                #{keyword} IS NULL OR #{keyword} = ''
                OR LOWER(s.submissionNo) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(s.title) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(
            """ + MemberPersonalDataSql.EMAIL + """
                ) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(CONCAT(
            """ + MemberPersonalDataSql.FIRST_NAME + """
                    , ' ',
            """ + MemberPersonalDataSql.LAST_NAME + """
                )) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(pt.codeName) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(acceptedPt.codeName) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(c.codeName) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                OR LOWER(s.status) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
            )
              AND (#{presentationTypeCode} IS NULL OR s.presentationTypeCode = #{presentationTypeCode})
              AND (#{acceptedPresentationTypeCode} IS NULL OR s.acceptedPresentationTypeCode = #{acceptedPresentationTypeCode})
              AND (#{categoryCode} IS NULL OR s.categoryCode = #{categoryCode})
              AND (#{status} IS NULL OR #{status} = '' OR s.status = #{status})
            ORDER BY s.createdAt DESC, s.seq DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<AbstractSubmissionResponse> findPage(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("keyword") String keyword,
            @Param("presentationTypeCode") Long presentationTypeCode,
            @Param("acceptedPresentationTypeCode") Long acceptedPresentationTypeCode,
            @Param("categoryCode") Long categoryCode,
            @Param("status") String status,
            @Param("size") int size,
            @Param("offset") int offset,
            @Param("dbEncString") String dbEncString
    );

    @Select("""
            SELECT
                s.seq,
                s.memberSeq,
                s.submissionSource,
                s.createdByAdminSeq,
                CONVERT(AES_DECRYPT(UNHEX(creator.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS createdByAdminName,
            """ + MemberPersonalDataSql.EMAIL + """
                AS memberEmail,
                CONCAT(
            """ + MemberPersonalDataSql.FIRST_NAME + """
                    , ' ',
            """ + MemberPersonalDataSql.LAST_NAME + """
                ) AS memberFullName,
                s.submissionNo,
                s.presentationTypeCode,
                pt.codeName AS presentationTypeName,
                s.acceptedPresentationTypeCode,
                acceptedPt.codeName AS acceptedPresentationTypeName,
                s.categoryCode,
                c.codeName AS categoryName,
                s.title,
                s.objectiveText,
                s.methodsText,
                s.resultsText,
                s.conclusionsText,
                s.aiUsage,
                s.aiVersionInfo,
                s.aiDataAnalysisUsed,
                s.plagiarismPolicyConfirmed,
                s.wordCount,
                s.status,
                s.decisionByAdminSeq,
                CONVERT(AES_DECRYPT(UNHEX(decisionAdmin.adminName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS decisionByAdminName,
                s.decisionAt,
                s.decisionReason,
                s.forcedDecision,
                (
                    SELECT COUNT(*) FROM abstract_submission_authors a
                    WHERE a.abstractSeq = s.seq
                ) AS authorCount,
                (
                    SELECT COUNT(*) FROM abstract_submission_institutions i
                    WHERE i.abstractSeq = s.seq
                ) AS institutionCount,
                (
                    SELECT COUNT(*) FROM abstract_review_assignments assignment
                    WHERE assignment.abstractSeq = s.seq
                      AND assignment.conferenceSeq = s.conferenceSeq
                      AND assignment.status IN ('assigned', 'accepted', 'in_review', 'completed')
                ) AS reviewerAssignmentCount,
                (
                    SELECT titleCheck.maxSimilarity
                    FROM abstract_title_similarity_checks titleCheck
                    WHERE titleCheck.conferenceSeq = s.conferenceSeq
                      AND titleCheck.abstractSeq = s.seq
                ) AS titleSimilarityMaxScore,
                (
                    SELECT titleCheck.matchCount
                    FROM abstract_title_similarity_checks titleCheck
                    WHERE titleCheck.conferenceSeq = s.conferenceSeq
                      AND titleCheck.abstractSeq = s.seq
                ) AS titleSimilarityMatchCount,
                (
                    SELECT titleCheck.algorithmVersion
                    FROM abstract_title_similarity_checks titleCheck
                    WHERE titleCheck.conferenceSeq = s.conferenceSeq
                      AND titleCheck.abstractSeq = s.seq
                ) AS titleSimilarityAlgorithmVersion,
                (
                    SELECT titleCheck.checkedAt
                    FROM abstract_title_similarity_checks titleCheck
                    WHERE titleCheck.conferenceSeq = s.conferenceSeq
                      AND titleCheck.abstractSeq = s.seq
                ) AS titleSimilarityCheckedAt,
                (
                    SELECT CONVERT(AES_DECRYPT(UNHEX(a.authorName), SHA2(#{dbEncString}, 512)) USING utf8mb4)
                    FROM abstract_submission_authors a
                    WHERE a.abstractSeq = s.seq
                    ORDER BY a.authorOrder
                    LIMIT 1
                ) AS mainAuthorName,
                s.submittedAt,
                s.reviewedAt,
                s.createdAt,
                s.updatedAt
            FROM abstract_submissions s
            JOIN members m ON m.seq = s.memberSeq AND m.conferenceSeq = s.conferenceSeq
            JOIN common_codes pt
              ON pt.seq = s.presentationTypeCode
             AND pt.groupCode = 'ABSTRACT_PRESENTATION_TYPES'
             AND pt.isDelete = 'N'
            LEFT JOIN common_codes acceptedPt
              ON acceptedPt.seq = s.acceptedPresentationTypeCode
             AND acceptedPt.groupCode = 'ABSTRACT_PRESENTATION_TYPES'
             AND acceptedPt.isDelete = 'N'
            LEFT JOIN admin_accounts creator ON creator.seq = s.createdByAdminSeq
            LEFT JOIN admin_accounts decisionAdmin ON decisionAdmin.seq = s.decisionByAdminSeq
            JOIN common_codes c
              ON c.seq = s.categoryCode
             AND c.groupCode = 'ABSTRACT_CATEGORY'
             AND c.isDelete = 'N'
            WHERE s.seq = #{seq}
              AND s.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    AbstractSubmissionResponse findBySeq(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("seq") Long seq,
            @Param("dbEncString") String dbEncString
    );

    @Select("""
            SELECT seq, submissionNo, title, objectiveText, methodsText,
                   resultsText, conclusionsText, status
            FROM abstract_submissions
            WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND status <> 'draft'
            ORDER BY seq ASC
            """)
    List<AbstractSubmissionResponse> findSimilarityCandidates(@Param("conferenceSeq") Long conferenceSeq);

    @Select("""
            SELECT COUNT(*)
            FROM abstract_submissions
            WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND status <> 'draft'
            """)
    long countSimilarityCandidates(@Param("conferenceSeq") Long conferenceSeq);

    @Select("""
            SELECT seq, submissionNo, title, categoryCode, status
            FROM abstract_submissions
            WHERE submissionNo = #{submissionNo}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    AbstractSubmissionResponse findBySubmissionNo(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("submissionNo") String submissionNo
    );

    @Select("""
            SELECT s.seq,
                   s.submissionNo,
                   pt.codeName AS presentationTypeName,
                   c.codeName AS categoryName,
                   s.title,
                   s.status,
                   (
                       SELECT COUNT(*)
                       FROM abstract_submission_authors author
                       WHERE author.abstractSeq = s.seq
                   ) AS authorCount,
                   s.submittedAt,
                   s.createdAt
            FROM abstract_submissions s
            LEFT JOIN common_codes pt
              ON pt.seq = s.presentationTypeCode
             AND pt.groupCode = 'ABSTRACT_PRESENTATION_TYPES'
             AND pt.isDelete = 'N'
            LEFT JOIN common_codes c
              ON c.seq = s.categoryCode
             AND c.groupCode = 'ABSTRACT_CATEGORY'
             AND c.isDelete = 'N'
            WHERE s.memberSeq = #{memberSeq}
              AND s.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            ORDER BY COALESCE(s.submittedAt, s.createdAt) DESC, s.seq DESC
            """)
    List<MemberAbstractSubmissionResponse> findByMemberSeq(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("memberSeq") Long memberSeq
    );

    @Select("""
            SELECT
                code.seq AS code,
                code.codeName AS name,
                code.sortOrder,
                TRUE AS enabled
            FROM common_codes code
            JOIN common_codes root
              ON root.seq = code.parentSeq
             AND root.parentSeq = 0
             AND root.groupCode = 'ABSTRACT_PRESENTATION_TYPES'
             AND root.isUsed = 'Y'
             AND root.isDelete = 'N'
            WHERE code.groupCode = 'ABSTRACT_PRESENTATION_TYPES'
              AND code.isUsed = 'Y'
              AND code.isDelete = 'N'
            ORDER BY code.sortOrder ASC, code.seq ASC
            """)
    List<AbstractPresentationTypeResponse> findEnabledPresentationTypes();

    @Select("""
            SELECT COUNT(*)
            FROM common_codes code
            JOIN common_codes root
              ON root.seq = code.parentSeq
             AND root.parentSeq = 0
             AND root.groupCode = 'ABSTRACT_PRESENTATION_TYPES'
             AND root.isUsed = 'Y'
             AND root.isDelete = 'N'
            WHERE code.seq = #{presentationTypeCode}
              AND code.groupCode = 'ABSTRACT_PRESENTATION_TYPES'
              AND code.isUsed = 'Y'
              AND code.isDelete = 'N'
            """)
    long countEnabledPresentationTypeByCode(@Param("presentationTypeCode") Long presentationTypeCode);

    @Select("""
            SELECT
                c.seq AS code,
                c.codeName AS name,
                c.sortOrder,
                TRUE AS enabled
            FROM common_codes c
            JOIN common_codes root
              ON root.seq = c.parentSeq
             AND root.parentSeq = 0
             AND root.groupCode = 'ABSTRACT_CATEGORY'
             AND root.isUsed = 'Y'
             AND root.isDelete = 'N'
            WHERE c.groupCode = 'ABSTRACT_CATEGORY'
              AND c.isUsed = 'Y'
              AND c.isDelete = 'N'
            ORDER BY c.sortOrder ASC, c.seq ASC
            """)
    List<AbstractCategoryResponse> findEnabledCategories();

    @Select("""
            SELECT COUNT(*)
            FROM common_codes c
            JOIN common_codes root
              ON root.seq = c.parentSeq
             AND root.parentSeq = 0
             AND root.groupCode = 'ABSTRACT_CATEGORY'
             AND root.isUsed = 'Y'
             AND root.isDelete = 'N'
            WHERE c.seq = #{categoryCode}
              AND c.groupCode = 'ABSTRACT_CATEGORY'
              AND c.isUsed = 'Y'
              AND c.isDelete = 'N'
            """)
    long countEnabledCategoryByCode(@Param("categoryCode") Long categoryCode);

    @Select("""
            SELECT
                code.seq AS code,
                code.codeName AS name,
                code.sortOrder,
                code.isEtc
            FROM common_codes code
            JOIN common_codes root
              ON root.seq = code.parentSeq
             AND root.parentSeq = 0
             AND root.groupCode = #{groupCode}
             AND root.isUsed = 'Y'
             AND root.isDelete = 'N'
            WHERE code.groupCode = #{groupCode}
              AND code.isUsed = 'Y'
              AND code.isDelete = 'N'
            ORDER BY code.sortOrder ASC, code.seq ASC
            """)
    List<AbstractAiOptionResponse> findEnabledAiOptions(@Param("groupCode") String groupCode);

    @Select("""
            SELECT COUNT(*)
            FROM common_codes code
            JOIN common_codes root
              ON root.seq = code.parentSeq
             AND root.parentSeq = 0
             AND root.groupCode = #{groupCode}
             AND root.isUsed = 'Y'
             AND root.isDelete = 'N'
            WHERE code.seq = #{code}
              AND code.groupCode = #{groupCode}
              AND code.isUsed = 'Y'
              AND code.isDelete = 'N'
            """)
    long countEnabledAiOption(
            @Param("groupCode") String groupCode,
            @Param("code") Long code
    );

    @Select("""
            SELECT COUNT(*)
            FROM common_codes code
            JOIN common_codes root
              ON root.seq = code.parentSeq
             AND root.parentSeq = 0
             AND root.groupCode = #{groupCode}
             AND root.isUsed = 'Y'
             AND root.isDelete = 'N'
            WHERE code.seq = #{code}
              AND code.groupCode = #{groupCode}
              AND code.isUsed = 'Y'
              AND code.isDelete = 'N'
              AND code.isEtc = 'Y'
            """)
    long countEnabledEtcAiOption(
            @Param("groupCode") String groupCode,
            @Param("code") Long code
    );

    @Insert("""
            INSERT INTO abstract_submissions (
                conferenceSeq, memberSeq, submissionSource, createdByAdminSeq,
                submissionNo, presentationTypeCode, categoryCode, title,
                objectiveText, methodsText, resultsText, conclusionsText,
                aiUsage, aiVersionInfo, aiDataAnalysisUsed, plagiarismPolicyConfirmed,
                wordCount, status, submittedAt, reviewedAt, createdAt, updatedAt
            ) VALUES (
                #{conferenceSeq,javaType=java.lang.Long}, #{memberSeq}, #{submissionSource}, #{createdByAdminSeq},
                #{submissionNo}, #{presentationTypeCode}, #{categoryCode}, #{title},
                #{objectiveText}, #{methodsText}, #{resultsText}, #{conclusionsText},
                #{aiUsage}, #{aiVersionInfo}, #{aiDataAnalysisUsed}, #{plagiarismPolicyConfirmed},
                #{wordCount}, #{status}, #{submittedAt}, #{reviewedAt}, NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insert(AbstractSubmissionRequest request);

    @Update("""
            UPDATE abstract_submissions
            SET memberSeq = #{memberSeq},
                presentationTypeCode = #{presentationTypeCode},
                categoryCode = #{categoryCode},
                title = #{title},
                objectiveText = #{objectiveText},
                methodsText = #{methodsText},
                resultsText = #{resultsText},
                conclusionsText = #{conclusionsText},
                aiUsage = #{aiUsage},
                aiVersionInfo = #{aiVersionInfo},
                aiDataAnalysisUsed = #{aiDataAnalysisUsed},
                plagiarismPolicyConfirmed = #{plagiarismPolicyConfirmed},
                wordCount = #{wordCount},
                updatedAt = NOW()
            WHERE seq = #{seq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    void update(AbstractSubmissionRequest request);

    @Update("""
            UPDATE abstract_submissions
            SET submissionNo = #{submissionNo}
            WHERE seq = #{seq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    void updateSubmissionNo(@Param("conferenceSeq") Long conferenceSeq,
                            @Param("seq") Long seq,
                            @Param("submissionNo") String submissionNo);

    @Update("""
            UPDATE abstract_submissions
            SET status = #{status},
                updatedAt = NOW()
            WHERE seq = #{seq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    void updateReviewStatus(@Param("conferenceSeq") Long conferenceSeq,
                            @Param("seq") Long seq,
                            @Param("status") String status);

    @Update("""
            UPDATE abstract_submissions
            SET status = #{decision},
                acceptedPresentationTypeCode = #{acceptedPresentationTypeCode},
                decisionByAdminSeq = #{adminSeq},
                decisionAt = NOW(),
                decisionReason = #{reason},
                forcedDecision = #{forcedDecision},
                reviewedAt = NOW(),
                updatedAt = NOW()
            WHERE seq = #{seq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND status IN ('submitted', 'under_review')
            """)
    int updateDecision(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("seq") Long seq,
            @Param("decision") String decision,
            @Param("acceptedPresentationTypeCode") Long acceptedPresentationTypeCode,
            @Param("adminSeq") Long adminSeq,
            @Param("reason") String reason,
            @Param("forcedDecision") boolean forcedDecision
    );

    @Delete("DELETE FROM abstract_submission_institutions WHERE abstractSeq = #{abstractSeq}")
    void deleteInstitutions(@Param("abstractSeq") Long abstractSeq);

    @Delete("DELETE FROM abstract_submission_authors WHERE abstractSeq = #{abstractSeq}")
    void deleteAuthors(@Param("abstractSeq") Long abstractSeq);

    @Delete("DELETE FROM abstract_submission_ai_tools WHERE abstractSeq = #{abstractSeq}")
    void deleteAiTools(@Param("abstractSeq") Long abstractSeq);

    @Delete("DELETE FROM abstract_submission_ai_scopes WHERE abstractSeq = #{abstractSeq}")
    void deleteAiScopes(@Param("abstractSeq") Long abstractSeq);

    // Locks only the member-owned row so its workflow status cannot change midway through draft deletion.
    @Select("""
            SELECT status
            FROM abstract_submissions
            WHERE seq = #{seq}
              AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND memberSeq = #{memberSeq,javaType=java.lang.Long}
              AND submissionSource = 'member'
            FOR UPDATE
            """)
    String findMemberStatusForUpdate(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("seq") Long seq,
            @Param("memberSeq") Long memberSeq
    );

    @Delete("DELETE FROM abstract_submissions WHERE seq = #{seq} AND conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}")
    void delete(@Param("conferenceSeq") Long conferenceSeq, @Param("seq") Long seq);

    @Insert("""
            INSERT INTO abstract_submission_institutions (
                abstractSeq, institutionNo, country, institutionName, department, createdAt, updatedAt
            ) VALUES (
                #{abstractSeq}, #{institutionNo}, #{country}, #{institutionName}, #{department}, NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insertInstitution(AbstractSubmissionInstitution institution);

    @Insert("""
            INSERT INTO abstract_submission_authors (
                abstractSeq, authorOrder, authorName, institutionNo,
                isPresentingAuthor, isCorrespondingAuthor, email, country,
                officeCountryCode, officePhoneNumber, mobileCountryCode, mobilePhoneNumber,
                createdAt, updatedAt
            ) VALUES (
                #{author.abstractSeq}, #{author.authorOrder},
                HEX(AES_ENCRYPT(#{author.authorName}, SHA2(#{dbEncString}, 512))), #{author.institutionNo},
                #{author.isPresentingAuthor}, #{author.isCorrespondingAuthor},
                HEX(AES_ENCRYPT(#{author.email}, SHA2(#{dbEncString}, 512))), #{author.country},
                HEX(AES_ENCRYPT(#{author.officeCountryCode}, SHA2(#{dbEncString}, 512))),
                HEX(AES_ENCRYPT(#{author.officePhoneNumber}, SHA2(#{dbEncString}, 512))),
                HEX(AES_ENCRYPT(#{author.mobileCountryCode}, SHA2(#{dbEncString}, 512))),
                HEX(AES_ENCRYPT(#{author.mobilePhoneNumber}, SHA2(#{dbEncString}, 512))),
                NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "author.seq")
    void insertAuthor(
            @Param("author") AbstractSubmissionAuthor author,
            @Param("dbEncString") String dbEncString
    );

    @Insert("""
            INSERT INTO abstract_submission_ai_tools (
                abstractSeq, aiToolCode, otherToolName, otherProviderName, createdAt, updatedAt
            ) VALUES (
                #{abstractSeq}, #{aiToolCode}, #{otherToolName}, #{otherProviderName}, NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insertAiTool(AbstractSubmissionAiTool aiTool);

    @Insert("""
            INSERT INTO abstract_submission_ai_scopes (
                abstractSeq, aiScopeCode, otherScopeText, createdAt, updatedAt
            ) VALUES (
                #{abstractSeq}, #{aiScopeCode}, #{otherScopeText}, NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insertAiScope(AbstractSubmissionAiScope aiScope);

    @Select("""
            SELECT seq, abstractSeq, institutionNo, country, institutionName, department
            FROM abstract_submission_institutions
            WHERE abstractSeq = #{abstractSeq}
            ORDER BY institutionNo ASC, seq ASC
            """)
    List<AbstractSubmissionInstitution> findInstitutionsByAbstractSeq(@Param("abstractSeq") Long abstractSeq);

    @Select("""
            SELECT seq, abstractSeq, authorOrder,
                   CONVERT(AES_DECRYPT(UNHEX(authorName), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS authorName,
                   institutionNo, isPresentingAuthor, isCorrespondingAuthor,
                   CONVERT(AES_DECRYPT(UNHEX(email), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS email,
                   country,
                   CONVERT(AES_DECRYPT(UNHEX(officeCountryCode), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS officeCountryCode,
                   CONVERT(AES_DECRYPT(UNHEX(officePhoneNumber), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS officePhoneNumber,
                   CONVERT(AES_DECRYPT(UNHEX(mobileCountryCode), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS mobileCountryCode,
                   CONVERT(AES_DECRYPT(UNHEX(mobilePhoneNumber), SHA2(#{dbEncString}, 512)) USING utf8mb4) AS mobilePhoneNumber
            FROM abstract_submission_authors
            WHERE abstractSeq = #{abstractSeq}
            ORDER BY authorOrder ASC, seq ASC
            """)
    List<AbstractSubmissionAuthor> findAuthorsByAbstractSeq(
            @Param("abstractSeq") Long abstractSeq,
            @Param("dbEncString") String dbEncString
    );

    @Select("""
            SELECT
                tool.seq,
                tool.abstractSeq,
                tool.aiToolCode,
                code.codeName AS aiToolName,
                code.isEtc,
                tool.otherToolName,
                tool.otherProviderName
            FROM abstract_submission_ai_tools tool
            JOIN common_codes code ON code.seq = tool.aiToolCode
            WHERE tool.abstractSeq = #{abstractSeq}
            ORDER BY code.sortOrder ASC, tool.seq ASC
            """)
    List<AbstractSubmissionAiTool> findAiToolsByAbstractSeq(@Param("abstractSeq") Long abstractSeq);

    @Select("""
            SELECT
                scope.seq,
                scope.abstractSeq,
                scope.aiScopeCode,
                code.codeName AS aiScopeName,
                code.isEtc,
                scope.otherScopeText
            FROM abstract_submission_ai_scopes scope
            JOIN common_codes code ON code.seq = scope.aiScopeCode
            WHERE scope.abstractSeq = #{abstractSeq}
            ORDER BY code.sortOrder ASC, scope.seq ASC
            """)
    List<AbstractSubmissionAiScope> findAiScopesByAbstractSeq(@Param("abstractSeq") Long abstractSeq);

    @Select("""
            SELECT seq, abstractSeq, originalFilename, contentType, fileExtension,
                   fileSize, createdAt, updatedAt
            FROM abstract_submission_attachments
            WHERE abstractSeq = #{abstractSeq}
            ORDER BY createdAt DESC, seq DESC
            """)
    List<AbstractSubmissionAttachmentResponse> findAttachmentsByAbstractSeq(
            @Param("abstractSeq") Long abstractSeq
    );

    @Select("""
            SELECT seq, abstractSeq, originalFilename, saveFilename, contentType,
                   fileExtension, fileSize, checksumSha256, createdAt, updatedAt
            FROM abstract_submission_attachments
            WHERE seq = #{attachmentSeq}
              AND abstractSeq = #{abstractSeq}
            """)
    AbstractSubmissionAttachment findAttachment(
            @Param("abstractSeq") Long abstractSeq,
            @Param("attachmentSeq") Long attachmentSeq
    );

    @Select("SELECT COUNT(*) FROM abstract_submission_attachments WHERE abstractSeq = #{abstractSeq}")
    long countAttachments(@Param("abstractSeq") Long abstractSeq);

    @Insert("""
            INSERT INTO abstract_submission_attachments (
                abstractSeq, originalFilename, saveFilename, contentType,
                fileExtension, fileSize, createdAt, updatedAt
            ) VALUES (
                #{abstractSeq}, #{originalFilename}, #{saveFilename}, #{contentType},
                #{fileExtension}, #{fileSize}, NOW(), NOW()
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "seq")
    void insertAttachment(AbstractSubmissionAttachment attachment);

    @Delete("""
            DELETE FROM abstract_submission_attachments
            WHERE seq = #{attachmentSeq}
              AND abstractSeq = #{abstractSeq}
            """)
    int deleteAttachment(
            @Param("abstractSeq") Long abstractSeq,
            @Param("attachmentSeq") Long attachmentSeq
    );
}
