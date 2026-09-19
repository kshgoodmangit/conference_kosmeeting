package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.AdminDashboardResponse.EventSummary;
import com.bjworld21.conference.dto.OperationalDashboardResponse.*;
import com.bjworld21.conference.repository.AdminDashboardRepository;
import com.bjworld21.conference.repository.OperationalDashboardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class OperationalDashboardService {
    private final OperationalDashboardRepository repository;
    private final AdminDashboardRepository events;
    private final Clock clock;

    @Autowired
    public OperationalDashboardService(OperationalDashboardRepository repository, AdminDashboardRepository events) {
        this(repository, events, Clock.system(ZoneId.of("Asia/Seoul")));
    }
    OperationalDashboardService(OperationalDashboardRepository repository, AdminDashboardRepository events, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Registration registration(Long conferenceSeq) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        EventSummary event = event(conferenceSeq);
        List<Daily> daily = emptyDays(today);
        merge(daily, repository.registrationDays(conferenceSeq, today.minusDays(6).atStartOfDay(), today.plusDays(1).atStartOfDay()));
        merge(daily, repository.paymentDays(conferenceSeq, today.minusDays(6).atStartOfDay(), today.plusDays(1).atStartOfDay()));
        return new Registration(today, now, event.getEventName(), List.of(
                new Deadline("얼리버드 마감", event.getEarlyBirdEndDate()),
                new Deadline("사전등록 마감", event.getRegistrationEndDate()),
                new Deadline("행사 개최", event.getEventStartDate())),
                repository.registrationCategories(conferenceSeq, now),
                repository.registrationAmounts(conferenceSeq, today.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                daily, repository.registrationPeriods(conferenceSeq));
    }

    @Transactional(readOnly = true)
    public Abstracts abstracts(Long conferenceSeq) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        EventSummary event = event(conferenceSeq);
        List<Daily> daily = emptyDays(today);
        merge(daily, repository.abstractDays(conferenceSeq, today.minusDays(6).atStartOfDay(), today.plusDays(1).atStartOfDay()));
        List<Decision> decisions = repository.abstractDecisions(conferenceSeq, now, AbstractDecisionService.REQUIRED_REVIEWER_COUNT);
        return new Abstracts(today, now, event.getEventName(), List.of(
                new Deadline("초록 접수 마감", event.getAbstractEndDate()),
                new Deadline("행사 개최", event.getEventStartDate())),
                repository.abstractFields(conferenceSeq, now, AbstractDecisionService.REQUIRED_REVIEWER_COUNT), decisions,
                decisions.stream().filter(row -> "pending".equals(row.getKey())).mapToLong(Decision::getValue).sum(),
                repository.abstractRegistrations(conferenceSeq), daily);
    }

    private EventSummary event(Long conferenceSeq) {
        EventSummary event = events.findEventSummary(conferenceSeq);
        if (event == null) return EventSummary.builder().eventName("학회 미설정").build();
        return event;
    }
    private List<Daily> emptyDays(LocalDate today) {
        List<Daily> days = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            Daily day = new Daily();
            day.setDate(today.minusDays(i));
            days.add(day);
        }
        return days;
    }
    private void merge(List<Daily> days, List<Daily> values) {
        for (Daily value : values) {
            days.stream().filter(day -> day.getDate().equals(value.getDate())).findFirst().ifPresent(day -> {
                day.setRegistered(day.getRegistered() + value.getRegistered());
                day.setPaid(day.getPaid() + value.getPaid());
                day.setSubmitted(day.getSubmitted() + value.getSubmitted());
            });
        }
    }
}
