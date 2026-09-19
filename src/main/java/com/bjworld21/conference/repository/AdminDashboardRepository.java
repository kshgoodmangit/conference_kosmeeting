package com.bjworld21.conference.repository;

import com.bjworld21.conference.dto.AdminDashboardResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AdminDashboardRepository {

    @Select("""
            SELECT
                eventName,
                eventStartDate,
                DATEDIFF(eventStartDate, CURDATE()) AS eventDday,
                abstractEndDate,
                DATEDIFF(abstractEndDate, CURDATE()) AS abstractDday,
                earlyBirdEndDate,
                DATEDIFF(earlyBirdEndDate, CURDATE()) AS earlyBirdDday,
                regularEndDate AS registrationEndDate,
                DATEDIFF(regularEndDate, CURDATE()) AS registrationDday,
                registrationCurrency
            FROM conference_settings
            WHERE seq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    AdminDashboardResponse.EventSummary findEventSummary(@Param("conferenceSeq") Long conferenceSeq);

    @Select("""
            SELECT
                (SELECT COUNT(*) FROM members WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}) AS memberCount,
                (SELECT COUNT(*) FROM members WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long} AND createdAt >= CURDATE()) AS todayMemberCount,
                COUNT(DISTINCT memberSeq) AS preRegistrationCount,
                SUM(CASE WHEN createdAt >= CURDATE() THEN 1 ELSE 0 END) AS todayPreRegistrationCount,
                SUM(CASE WHEN applicationStatus = 'SUBMITTED' AND paymentStatus = 'PAID' THEN 1 ELSE 0 END) AS paidCount,
                SUM(CASE WHEN applicationStatus = 'SUBMITTED' AND paymentStatus = 'PAID' AND paidAt >= CURDATE() THEN 1 ELSE 0 END) AS todayPaidCount,
                SUM(CASE WHEN applicationStatus = 'SUBMITTED' AND paymentStatus = 'UNPAID' THEN 1 ELSE 0 END) AS unpaidCount,
                SUM(CASE WHEN applicationStatus = 'SUBMITTED' AND paymentStatus = 'FAILED' THEN 1 ELSE 0 END) AS failedCount,
                SUM(CASE WHEN applicationStatus = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelledCount,
                SUM(CASE WHEN applicationStatus = 'SUBMITTED' AND paymentStatus = 'REFUNDED' THEN 1 ELSE 0 END) AS refundedCount,
                COALESCE(SUM(CASE
                    WHEN applicationStatus = 'SUBMITTED' AND paymentStatus = 'PAID'
                        THEN COALESCE(paidAmount, totalAmount, feeAmount)
                    ELSE 0
                END), 0) AS paidAmount
            FROM pre_registrations
            WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    AdminDashboardResponse.RegistrationSummary findRegistrationSummary(@Param("conferenceSeq") Long conferenceSeq);

    @Select("""
            SELECT
                (SELECT COUNT(*)
                 FROM abstract_submissions
                 WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
                   AND status <> 'draft') AS submittedCount,
                (SELECT COUNT(*)
                 FROM abstract_submissions
                 WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
                   AND status <> 'draft'
                   AND COALESCE(submittedAt, createdAt) >= CURDATE()) AS todaySubmittedCount,
                (SELECT COUNT(*)
                 FROM abstract_submissions submission
                 WHERE submission.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
                   AND submission.status <> 'draft'
                   AND NOT EXISTS (
                       SELECT 1
                       FROM abstract_review_assignments assignment
                       WHERE assignment.abstractSeq = submission.seq
                         AND assignment.conferenceSeq = submission.conferenceSeq
                         AND assignment.status IN ('assigned', 'accepted', 'in_review', 'completed')
                   )) AS unassignedCount,
                (SELECT COUNT(DISTINCT abstractSeq)
                 FROM abstract_review_assignments
                 WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
                   AND status IN ('assigned', 'accepted', 'in_review', 'completed')) AS assignedCount,
                (SELECT COUNT(DISTINCT completed.abstractSeq)
                 FROM abstract_review_assignments completed
                 WHERE completed.conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
                   AND completed.status = 'completed'
                   AND NOT EXISTS (
                       SELECT 1
                       FROM abstract_review_assignments pending
                       WHERE pending.abstractSeq = completed.abstractSeq
                         AND pending.conferenceSeq = completed.conferenceSeq
                         AND pending.status IN ('assigned', 'accepted', 'in_review')
                   )) AS completedCount,
                (SELECT COUNT(*)
                 FROM abstract_submissions
                 WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
                   AND status IN ('approved', 'rejected')) AS resultConfirmedCount,
                (SELECT COUNT(*)
                 FROM abstract_review_assignments
                 WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
                   AND status IN ('assigned', 'accepted', 'in_review')
                   AND dueAt IS NOT NULL
                   AND dueAt < NOW()) AS overdueCount
            """)
    AdminDashboardResponse.AbstractReviewSummary findAbstractReviewSummary(@Param("conferenceSeq") Long conferenceSeq);

    @Select("""
            SELECT
                COUNT(*) AS applicationCount,
                COALESCE(SUM(CASE WHEN isDeposited = TRUE THEN 1 ELSE 0 END), 0) AS depositedCount,
                COALESCE(SUM(CASE WHEN isDeposited = FALSE THEN 1 ELSE 0 END), 0) AS unpaidCount,
                COALESCE(SUM(CASE WHEN taxInvoiceIssueDate IS NOT NULL THEN 1 ELSE 0 END), 0) AS taxInvoiceIssuedCount,
                COALESCE(SUM(CASE
                    WHEN isDeposited = FALSE
                     AND expectedDepositDate IS NOT NULL
                     AND expectedDepositDate < CURDATE() THEN 1
                    ELSE 0
                END), 0) AS overdueDepositCount,
                COALESCE(SUM(CASE
                    WHEN isDeposited = TRUE
                     AND taxInvoiceIssueDate IS NULL THEN 1
                    ELSE 0
                END), 0) AS taxInvoicePendingCount,
                COALESCE(SUM(sponsorshipAmount), 0) AS totalAmount,
                COALESCE(SUM(CASE WHEN isDeposited = TRUE THEN sponsorshipAmount ELSE 0 END), 0) AS depositedAmount
            FROM sponsorship_applications
            WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
            """)
    AdminDashboardResponse.SponsorshipSummary findSponsorshipSummary(@Param("conferenceSeq") Long conferenceSeq);

    @Select("""
            SELECT
                days.activityDate,
                COALESCE(abstracts.abstractCount, 0) AS abstractCount,
                COALESCE(registrations.registrationCount, 0) AS registrationCount
            FROM (
                SELECT CURDATE() - INTERVAL 6 DAY AS activityDate
                UNION ALL SELECT CURDATE() - INTERVAL 5 DAY
                UNION ALL SELECT CURDATE() - INTERVAL 4 DAY
                UNION ALL SELECT CURDATE() - INTERVAL 3 DAY
                UNION ALL SELECT CURDATE() - INTERVAL 2 DAY
                UNION ALL SELECT CURDATE() - INTERVAL 1 DAY
                UNION ALL SELECT CURDATE()
            ) days
            LEFT JOIN (
                SELECT DATE(COALESCE(submittedAt, createdAt)) AS activityDate, COUNT(*) AS abstractCount
                FROM abstract_submissions
                WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
                  AND status <> 'draft'
                  AND COALESCE(submittedAt, createdAt) >= CURDATE() - INTERVAL 6 DAY
                GROUP BY DATE(COALESCE(submittedAt, createdAt))
            ) abstracts ON abstracts.activityDate = days.activityDate
            LEFT JOIN (
                SELECT DATE(createdAt) AS activityDate, COUNT(*) AS registrationCount
                FROM pre_registrations
                WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
                  AND createdAt >= CURDATE() - INTERVAL 6 DAY
                GROUP BY DATE(createdAt)
            ) registrations ON registrations.activityDate = days.activityDate
            ORDER BY days.activityDate
            """)
    List<AdminDashboardResponse.DailyTrend> findDailyTrends(@Param("conferenceSeq") Long conferenceSeq);

    @Select("""
            SELECT
                'MAIL' AS type,
                subject AS label,
                scheduledAt,
                DATEDIFF(DATE(scheduledAt), CURDATE()) AS dday
            FROM mail_campaigns
            WHERE conferenceSeq = #{conferenceSeq,javaType=java.lang.Long}
              AND status = 'SCHEDULED'
              AND scheduledAt >= NOW()
            ORDER BY scheduledAt ASC, seq ASC
            LIMIT 1
            """)
    AdminDashboardResponse.UpcomingSchedule findNextScheduledMail(@Param("conferenceSeq") Long conferenceSeq);
}
