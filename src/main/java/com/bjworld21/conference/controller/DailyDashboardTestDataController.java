package com.bjworld21.conference.controller;

import com.bjworld21.conference.service.DailyDashboardTestDataService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 대시보드 일별 샘플 데이터 전용 임시 API입니다. */
@RestController
@RequestMapping("/api/admin/testdata/daily")
public class DailyDashboardTestDataController {
    private static final Logger log = LoggerFactory.getLogger(DailyDashboardTestDataController.class);

    private final DailyDashboardTestDataService service;

    public DailyDashboardTestDataController(DailyDashboardTestDataService service) {
        this.service = service;
    }

    @org.springframework.web.bind.annotation.GetMapping("/range")
    public DailyDashboardTestDataService.DateRange range(@RequestHeader("X-Conference-Seq") Long conferenceSeq) {
        return service.conferenceRange(conferenceSeq);
    }

    @PostMapping("/members")
    public ResponseEntity<?> createMembers(@RequestHeader("X-Conference-Seq") Long conferenceSeq) {
        return execute(() -> service.createMembersFromStartDate(conferenceSeq), "일별 회원 테스트 데이터 생성 중 오류가 발생했습니다.");
    }

    @PostMapping("/pre-registrations")
    public ResponseEntity<?> createPreRegistrations(@RequestHeader("X-Conference-Seq") Long conferenceSeq) {
        return execute(
                () -> service.createPreRegistrationsFromStartDate(conferenceSeq),
                "일별 사전등록 테스트 데이터 생성 중 오류가 발생했습니다."
        );
    }

    @PostMapping("/abstracts")
    public ResponseEntity<?> createAbstracts(@RequestHeader("X-Conference-Seq") Long conferenceSeq, HttpSession session) {
        long adminSeq = ((Number) session.getAttribute("adminSeq")).longValue();
        return execute(
                () -> service.createAbstractsFromStartDate(conferenceSeq, adminSeq),
                "일별 초록 테스트 데이터 생성 중 오류가 발생했습니다."
        );
    }

    private ResponseEntity<?> execute(DailyCreation creation, String errorMessage) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(creation.create());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        } catch (Exception exception) {
            log.error(errorMessage, exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMessage);
        }
    }

    @FunctionalInterface
    private interface DailyCreation {
        DailyDashboardTestDataService.DailyCreationResult create();
    }
}
