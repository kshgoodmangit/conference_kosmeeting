package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.AdminDashboardResponse;
import com.bjworld21.conference.repository.AdminDashboardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {
    @Mock
    private AdminDashboardRepository adminDashboardRepository;

    @InjectMocks
    private AdminDashboardService adminDashboardService;

    @Test
    void getDashboardBuildsOperationalSummariesAndSchedules() {
        AdminDashboardResponse.EventSummary event = AdminDashboardResponse.EventSummary.builder()
                .eventName("APDRC8")
                .eventStartDate(LocalDate.of(2027, 5, 9))
                .eventDday(257L)
                .abstractEndDate(LocalDate.of(2026, 9, 15))
                .abstractDday(21L)
                .earlyBirdEndDate(LocalDate.of(2026, 9, 30))
                .earlyBirdDday(36L)
                .registrationEndDate(LocalDate.of(2027, 3, 31))
                .registrationDday(218L)
                .registrationCurrency("USD")
                .build();
        AdminDashboardResponse.RegistrationSummary registration = AdminDashboardResponse.RegistrationSummary.builder()
                .memberCount(317L)
                .preRegistrationCount(276L)
                .paidCount(179L)
                .unpaidCount(51L)
                .paidAmount(new BigDecimal("63850.00"))
                .build();
        AdminDashboardResponse.AbstractReviewSummary abstractReview = AdminDashboardResponse.AbstractReviewSummary.builder()
                .submittedCount(217L)
                .assignedCount(181L)
                .completedCount(82L)
                .resultConfirmedCount(72L)
                .build();
        AdminDashboardResponse.SponsorshipSummary sponsorship = AdminDashboardResponse.SponsorshipSummary.builder()
                .applicationCount(20L)
                .depositedCount(12L)
                .unpaidCount(8L)
                .taxInvoiceIssuedCount(9L)
                .overdueDepositCount(3L)
                .taxInvoicePendingCount(3L)
                .totalAmount(new BigDecimal("100000000"))
                .depositedAmount(new BigDecimal("65000000"))
                .build();
        AdminDashboardResponse.DailyTrend trend = AdminDashboardResponse.DailyTrend.builder()
                .activityDate(LocalDate.of(2026, 8, 25))
                .abstractCount(2L)
                .registrationCount(2L)
                .build();
        AdminDashboardResponse.UpcomingSchedule nextMail = AdminDashboardResponse.UpcomingSchedule.builder()
                .type("MAIL")
                .label("2차 안내")
                .scheduledAt(LocalDateTime.of(2026, 9, 10, 10, 0))
                .dday(16L)
                .build();

        when(adminDashboardRepository.findEventSummary(1L)).thenReturn(event);
        when(adminDashboardRepository.findRegistrationSummary(1L)).thenReturn(registration);
        when(adminDashboardRepository.findAbstractReviewSummary(1L)).thenReturn(abstractReview);
        when(adminDashboardRepository.findSponsorshipSummary(1L)).thenReturn(sponsorship);
        when(adminDashboardRepository.findDailyTrends(1L)).thenReturn(List.of(trend));
        when(adminDashboardRepository.findNextScheduledMail(1L)).thenReturn(nextMail);

        AdminDashboardResponse result = adminDashboardService.getDashboard(1L);

        assertThat(result.getEvent().getEventName()).isEqualTo("APDRC8");
        assertThat(result.getRegistration().getPaymentRate()).isEqualByComparingTo("64.9");
        assertThat(result.getAbstractReview().getCompletionRate()).isEqualByComparingTo("45.3");
        assertThat(result.getSponsorship().getDepositRate()).isEqualByComparingTo("65.0");
        assertThat(result.getSponsorship().getOverdueDepositCount()).isEqualTo(3L);
        assertThat(result.getTrends()).containsExactly(trend);
        assertThat(result.getUpcomingSchedules())
                .extracting(AdminDashboardResponse.UpcomingSchedule::getType)
                .containsExactly("MAIL", "ABSTRACT", "EARLY_BIRD", "REGISTRATION");
    }

    @Test
    void getDashboardUsesEmptyDefaultsWhenSettingsAreMissing() {
        when(adminDashboardRepository.findEventSummary(1L)).thenReturn(null);
        when(adminDashboardRepository.findRegistrationSummary(1L)).thenReturn(null);
        when(adminDashboardRepository.findAbstractReviewSummary(1L)).thenReturn(null);
        when(adminDashboardRepository.findSponsorshipSummary(1L)).thenReturn(null);
        when(adminDashboardRepository.findDailyTrends(1L)).thenReturn(null);
        when(adminDashboardRepository.findNextScheduledMail(1L)).thenReturn(null);

        AdminDashboardResponse result = adminDashboardService.getDashboard(1L);

        assertThat(result.getEvent().getEventName()).isEqualTo("학회명");
        assertThat(result.getRegistration().getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getRegistration().getPaymentRate()).isEqualByComparingTo("0.0");
        assertThat(result.getAbstractReview().getCompletionRate()).isEqualByComparingTo("0.0");
        assertThat(result.getSponsorship().getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getSponsorship().getDepositRate()).isEqualByComparingTo("0.0");
        assertThat(result.getTrends()).isEmpty();
        assertThat(result.getUpcomingSchedules()).isEmpty();
    }
}
