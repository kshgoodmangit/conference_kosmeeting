package com.bjworld21.congress.repository;

import com.bjworld21.congress.dto.OperationalDashboardResponse.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OperationalDashboardRepository {
    @Select("""
            SELECT c.seq AS categorySeq, c.categoryName,
                COALESCE(SUM(p.applicationStatus = 'SUBMITTED' AND p.paymentStatus = 'PAID'), 0) AS paid,
                COALESCE(SUM(p.applicationStatus = 'SUBMITTED' AND p.paymentStatus IN ('UNPAID', 'FAILED')), 0) AS unpaid,
                COALESCE(SUM(p.applicationStatus = 'SUBMITTED' AND p.paymentStatus IN ('UNPAID', 'FAILED')
                    AND p.createdAt <= DATE_SUB(#{now}, INTERVAL 7 DAY)), 0) AS longUnpaid,
                COALESCE(SUM(p.applicationStatus = 'CANCELLED' AND p.paymentStatus <> 'REFUNDED'), 0) AS cancelled,
                COALESCE(SUM(p.paymentStatus = 'REFUNDED'), 0) AS refunded
            FROM registration_categories c
            LEFT JOIN pre_registrations p ON p.categorySeq = c.seq AND p.conferenceSeq = c.conferenceSeq
            WHERE c.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND (c.isDelete = 'N' OR p.seq IS NOT NULL)
            GROUP BY c.seq, c.categoryName, c.sortOrder
            ORDER BY c.sortOrder, c.seq
            """)
    List<RegistrationCategory> registrationCategories(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("now") LocalDateTime now
    );

    @Select("""
            SELECT currencies.currency,
                COALESCE(SUM(CASE WHEN p.applicationStatus = 'SUBMITTED' AND p.paymentStatus = 'PAID'
                    THEN COALESCE(p.paidAmount, p.totalAmount, p.feeAmount) ELSE 0 END), 0) AS paid,
                COALESCE(SUM(CASE WHEN p.applicationStatus = 'SUBMITTED' AND p.paymentStatus = 'PAID'
                    AND p.paidAt >= #{today} AND p.paidAt < #{tomorrow}
                    THEN COALESCE(p.paidAmount, p.totalAmount, p.feeAmount) ELSE 0 END), 0) AS todayPaid,
                COALESCE(SUM(CASE WHEN p.applicationStatus = 'SUBMITTED' AND p.paymentStatus IN ('UNPAID', 'FAILED')
                    THEN COALESCE(p.totalAmount, p.feeAmount) ELSE 0 END), 0) AS unpaid
            FROM (SELECT 'KRW' AS currency UNION SELECT 'USD' UNION SELECT currency FROM pre_registrations WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}) currencies
            LEFT JOIN pre_registrations p ON p.currency = currencies.currency AND p.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            GROUP BY currencies.currency ORDER BY currencies.currency
            """)
    List<Amount> registrationAmounts(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("today") LocalDateTime today,
            @Param("tomorrow") LocalDateTime tomorrow
    );

    @Select("""
            SELECT CASE periodType WHEN 'EARLY_BIRD' THEN '얼리버드' WHEN 'REGULAR' THEN '일반등록'
                ELSE periodType END AS label, COUNT(*) AS count
            FROM pre_registrations
            WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            GROUP BY periodType ORDER BY periodType
            """)
    List<Period> registrationPeriods(@Param("conferenceSeq") Long conferenceSeq);

    @Select("""
            SELECT DATE(createdAt) AS date, COUNT(*) AS registered
            FROM pre_registrations WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND createdAt >= #{start} AND createdAt < #{end}
            GROUP BY DATE(createdAt)
            """)
    List<Daily> registrationDays(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    @Select("""
            SELECT DATE(paidAt) AS date, COUNT(*) AS paid
            FROM pre_registrations WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND applicationStatus = 'SUBMITTED' AND paymentStatus = 'PAID'
                AND paidAt >= #{start} AND paidAt < #{end}
            GROUP BY DATE(paidAt)
            """)
    List<Daily> paymentDays(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    // Always aggregate assignments to one row per abstract before grouping by category/decision.
    // A completed assignment alone is insufficient: a submitted review must also exist.
    String ABSTRACT_STATE = """
            WITH review_state AS (
                SELECT a.abstractSeq, COUNT(*) AS assignedCount,
                    SUM(a.status = 'completed' AND r.status = 'submitted') AS completedCount,
                    MAX(CASE WHEN (a.status <> 'completed' OR COALESCE(r.status, 'draft') <> 'submitted')
                        AND a.dueAt < #{now} THEN 1 ELSE 0 END) AS overdue
                FROM abstract_review_assignments a
                LEFT JOIN abstract_reviews r ON r.assignmentSeq = a.seq
                WHERE a.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
                  AND a.status IN ('assigned', 'accepted', 'in_review', 'completed')
                GROUP BY a.abstractSeq
            ), submissions AS (
                SELECT s.*, COALESCE(r.assignedCount, 0) AS assignedCount,
                    CASE WHEN r.assignedCount >= #{requiredReviewers} AND r.completedCount = r.assignedCount
                        THEN 1 ELSE 0 END AS complete,
                    CASE WHEN s.status IN ('submitted', 'under_review') THEN COALESCE(r.overdue, 0) ELSE 0 END AS overdue
                FROM abstract_submissions s LEFT JOIN review_state r ON r.abstractSeq = s.seq
                WHERE s.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
                  AND s.status IN ('submitted', 'under_review', 'approved', 'rejected')
            )
            """;

    @Select(ABSTRACT_STATE + """
            SELECT c.seq AS categorySeq, c.codeName AS name,
                COALESCE(SUM(s.seq IS NOT NULL AND s.assignedCount = 0), 0) AS unassigned,
                COALESCE(SUM(s.assignedCount > 0 AND s.complete = 0), 0) AS reviewing,
                COALESCE(SUM(s.complete), 0) AS completed,
                COALESCE(SUM(s.overdue), 0) AS overdue
            FROM common_codes c LEFT JOIN submissions s ON s.categoryCode = c.seq
            WHERE c.groupCode = 'ABSTRACT_CATEGORY' AND c.parentSeq IS NOT NULL
                AND (c.isDelete = 'N' OR s.seq IS NOT NULL)
            GROUP BY c.seq, c.codeName, c.sortOrder ORDER BY c.sortOrder, c.seq
            """)
    List<Field> abstractFields(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("now") LocalDateTime now,
            @Param("requiredReviewers") int requiredReviewers
    );

    @Select(ABSTRACT_STATE + """
            SELECT CASE WHEN s.status = 'approved' THEN CONCAT('approved-', COALESCE(s.acceptedPresentationTypeCode, 0))
                    WHEN s.status = 'rejected' THEN 'rejected'
                    WHEN s.complete = 1 THEN 'pending' ELSE 'reviewing' END AS `key`,
                CASE WHEN s.status = 'approved' THEN CONCAT(COALESCE(c.codeName, '발표형식 미지정'), ' 채택')
                    WHEN s.status = 'rejected' THEN '미채택'
                    WHEN s.complete = 1 THEN '결과 확정 대기' ELSE '심사 미완료' END AS label,
                COUNT(*) AS value
            FROM submissions s LEFT JOIN common_codes c ON c.seq = s.acceptedPresentationTypeCode
            GROUP BY `key`, label ORDER BY `key`
            """)
    List<Decision> abstractDecisions(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("now") LocalDateTime now,
            @Param("requiredReviewers") int requiredReviewers
    );

    @Select("""
            SELECT COALESCE(s.acceptedPresentationTypeCode, 0) AS presentationTypeSeq,
                COALESCE(c.codeName, '발표형식 미지정') AS label,
                SUM(CASE WHEN EXISTS (SELECT 1 FROM pre_registrations p
                    WHERE p.memberSeq = s.memberSeq AND p.conferenceSeq = s.conferenceSeq
                        AND p.applicationStatus = 'SUBMITTED'
                        AND p.paymentStatus <> 'REFUNDED') THEN 1 ELSE 0 END) AS registered,
                SUM(CASE WHEN NOT EXISTS (SELECT 1 FROM pre_registrations p
                    WHERE p.memberSeq = s.memberSeq AND p.conferenceSeq = s.conferenceSeq
                        AND p.applicationStatus = 'SUBMITTED'
                        AND p.paymentStatus <> 'REFUNDED') THEN 1 ELSE 0 END) AS unregistered
            FROM abstract_submissions s LEFT JOIN common_codes c ON c.seq = s.acceptedPresentationTypeCode
            WHERE s.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND s.status = 'approved'
            GROUP BY s.acceptedPresentationTypeCode, c.codeName ORDER BY presentationTypeSeq
            """)
    List<Presenter> abstractRegistrations(@Param("conferenceSeq") Long conferenceSeq);

    @Select("""
            SELECT DATE(COALESCE(submittedAt, createdAt)) AS date, COUNT(*) AS submitted
            FROM abstract_submissions
            WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
                AND status IN ('submitted', 'under_review', 'approved', 'rejected')
                AND COALESCE(submittedAt, createdAt) >= #{start} AND COALESCE(submittedAt, createdAt) < #{end}
            GROUP BY DATE(COALESCE(submittedAt, createdAt))
            """)
    List<Daily> abstractDays(
            @Param("conferenceSeq") Long conferenceSeq,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
