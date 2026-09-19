package com.bjworld21.conference.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ShowcaseDashboardResponse(
        long conferenceSeq, ConferenceSettingsResponse event, LocalDate asOf, LocalDateTime generatedAt,
        LocalDate trendStartDate, LocalDate trendEndDate,
        long memberCount, long todayMemberCount, long todayRegistrationCount, long todayAbstractCount,
        Registration registration, List<OperationalDashboardResponse.Amount> amounts,
        List<OperationalDashboardResponse.Field> fields, List<OperationalDashboardResponse.Daily> trends,
        List<Country> countries, List<String> partners, AdminDashboardResponse.SponsorshipSummary sponsorship) {
    public record Registration(long paidCount, long unpaidCount) {}
    public record Country(String code, String name, long value) {}
}
