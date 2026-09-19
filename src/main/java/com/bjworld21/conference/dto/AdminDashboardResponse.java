package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDashboardResponse {
    private EventSummary event;
    private RegistrationSummary registration;
    private AbstractReviewSummary abstractReview;
    private SponsorshipSummary sponsorship;
    private List<DailyTrend> trends;
    private List<UpcomingSchedule> upcomingSchedules;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EventSummary {
        private String eventName;
        private LocalDate eventStartDate;
        private Long eventDday;
        private LocalDate abstractEndDate;
        private Long abstractDday;
        private LocalDate earlyBirdEndDate;
        private Long earlyBirdDday;
        private LocalDate registrationEndDate;
        private Long registrationDday;
        private String registrationCurrency;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RegistrationSummary {
        private long memberCount;
        private long todayMemberCount;
        private long preRegistrationCount;
        private long todayPreRegistrationCount;
        private long paidCount;
        private long todayPaidCount;
        private long unpaidCount;
        private long failedCount;
        private long cancelledCount;
        private long refundedCount;
        private BigDecimal paidAmount;
        private BigDecimal paymentRate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AbstractReviewSummary {
        private long submittedCount;
        private long todaySubmittedCount;
        private long unassignedCount;
        private long assignedCount;
        private long completedCount;
        private long resultConfirmedCount;
        private long overdueCount;
        private BigDecimal completionRate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SponsorshipSummary {
        private long applicationCount;
        private long depositedCount;
        private long unpaidCount;
        private long taxInvoiceIssuedCount;
        private long overdueDepositCount;
        private long taxInvoicePendingCount;
        private BigDecimal totalAmount;
        private BigDecimal depositedAmount;
        private BigDecimal depositRate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailyTrend {
        private LocalDate activityDate;
        private long abstractCount;
        private long registrationCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpcomingSchedule {
        private String type;
        private String label;
        private LocalDate date;
        private LocalDateTime scheduledAt;
        private Long dday;
    }
}
