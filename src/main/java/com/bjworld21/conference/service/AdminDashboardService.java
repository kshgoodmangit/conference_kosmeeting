package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.AdminDashboardResponse;
import com.bjworld21.conference.repository.AdminDashboardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AdminDashboardService {
    private final AdminDashboardRepository adminDashboardRepository;

    public AdminDashboardService(AdminDashboardRepository adminDashboardRepository) {
        this.adminDashboardRepository = adminDashboardRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard(Long conferenceSeq) {
        AdminDashboardResponse.EventSummary event = adminDashboardRepository.findEventSummary(conferenceSeq);
        AdminDashboardResponse.RegistrationSummary registration = adminDashboardRepository.findRegistrationSummary(conferenceSeq);
        AdminDashboardResponse.AbstractReviewSummary abstractReview = adminDashboardRepository.findAbstractReviewSummary(conferenceSeq);
        AdminDashboardResponse.SponsorshipSummary sponsorship = adminDashboardRepository.findSponsorshipSummary(conferenceSeq);

        if (event == null) {
            event = AdminDashboardResponse.EventSummary.builder()
                    .eventName("학회명")
                    .registrationCurrency("USD")
                    .build();
        }
        if (registration == null) {
            registration = AdminDashboardResponse.RegistrationSummary.builder()
                    .paidAmount(BigDecimal.ZERO)
                    .build();
        }
        if (abstractReview == null) {
            abstractReview = AdminDashboardResponse.AbstractReviewSummary.builder().build();
        }
        if (sponsorship == null) {
            sponsorship = AdminDashboardResponse.SponsorshipSummary.builder()
                    .totalAmount(BigDecimal.ZERO)
                    .depositedAmount(BigDecimal.ZERO)
                    .build();
        }

        registration.setPaidAmount(defaultZero(registration.getPaidAmount()));
        registration.setPaymentRate(percentage(registration.getPaidCount(), registration.getPreRegistrationCount()));
        abstractReview.setCompletionRate(percentage(abstractReview.getCompletedCount(), abstractReview.getAssignedCount()));
        sponsorship.setTotalAmount(defaultZero(sponsorship.getTotalAmount()));
        sponsorship.setDepositedAmount(defaultZero(sponsorship.getDepositedAmount()));
        sponsorship.setDepositRate(percentage(sponsorship.getDepositedAmount(), sponsorship.getTotalAmount()));

        List<AdminDashboardResponse.DailyTrend> trends = adminDashboardRepository.findDailyTrends(conferenceSeq);
        List<AdminDashboardResponse.UpcomingSchedule> schedules = buildUpcomingSchedules(
                event,
                adminDashboardRepository.findNextScheduledMail(conferenceSeq)
        );

        return AdminDashboardResponse.builder()
                .event(event)
                .registration(registration)
                .abstractReview(abstractReview)
                .sponsorship(sponsorship)
                .trends(trends == null ? List.of() : trends)
                .upcomingSchedules(schedules)
                .build();
    }

    private List<AdminDashboardResponse.UpcomingSchedule> buildUpcomingSchedules(
            AdminDashboardResponse.EventSummary event,
            AdminDashboardResponse.UpcomingSchedule nextMail
    ) {
        List<AdminDashboardResponse.UpcomingSchedule> schedules = new ArrayList<>();

        if (event.getAbstractEndDate() != null && isUpcoming(event.getAbstractDday())) {
            schedules.add(AdminDashboardResponse.UpcomingSchedule.builder()
                    .type("ABSTRACT")
                    .label("초록 접수 마감")
                    .date(event.getAbstractEndDate())
                    .dday(event.getAbstractDday())
                    .build());
        }
        if (event.getEarlyBirdEndDate() != null && isUpcoming(event.getEarlyBirdDday())) {
            schedules.add(AdminDashboardResponse.UpcomingSchedule.builder()
                    .type("EARLY_BIRD")
                    .label("얼리버드 등록 마감")
                    .date(event.getEarlyBirdEndDate())
                    .dday(event.getEarlyBirdDday())
                    .build());
        }
        if (event.getRegistrationEndDate() != null && isUpcoming(event.getRegistrationDday())) {
            schedules.add(AdminDashboardResponse.UpcomingSchedule.builder()
                    .type("REGISTRATION")
                    .label("사전등록 마감")
                    .date(event.getRegistrationEndDate())
                    .dday(event.getRegistrationDday())
                    .build());
        }
        if (nextMail != null) {
            schedules.add(nextMail);
        }

        schedules.sort(Comparator.comparing(this::scheduleDateTime));
        return schedules;
    }

    private LocalDateTime scheduleDateTime(AdminDashboardResponse.UpcomingSchedule schedule) {
        if (schedule.getScheduledAt() != null) {
            return schedule.getScheduledAt();
        }
        return schedule.getDate().atStartOfDay();
    }

    private boolean isUpcoming(Long dday) {
        return dday == null || dday >= 0;
    }

    private BigDecimal percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.signum() <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return numerator
                .multiply(BigDecimal.valueOf(100))
                .divide(denominator, 1, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
