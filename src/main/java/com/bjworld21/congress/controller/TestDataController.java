package com.bjworld21.congress.controller;

import com.bjworld21.congress.service.TestDataService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/testdata")
public class TestDataController {
    private static final Logger log = LoggerFactory.getLogger(TestDataController.class);

    private final TestDataService testDataService;

    public TestDataController(TestDataService testDataService) {
        this.testDataService = testDataService;
    }

    @PostMapping("/members")
    public ResponseEntity<?> createMembers(@RequestHeader("X-Conference-Seq") Long conferenceSeq) {
        return execute(() -> testDataService.createMembers(conferenceSeq), "회원 테스트 데이터 생성 중 오류가 발생했습니다.");
    }

    @PostMapping("/pre-registrations")
    public ResponseEntity<?> createPreRegistrations(@RequestHeader("X-Conference-Seq") Long conferenceSeq) {
        return execute(() -> testDataService.createPreRegistrations(conferenceSeq), "사전등록 테스트 데이터 생성 중 오류가 발생했습니다.");
    }

    @PostMapping("/abstracts")
    public ResponseEntity<?> createAbstracts(@RequestHeader("X-Conference-Seq") Long conferenceSeq, HttpSession session) {
        long adminSeq = ((Number) session.getAttribute("adminSeq")).longValue();
        return execute(
                () -> testDataService.createAbstracts(conferenceSeq, adminSeq),
                "초록 테스트 데이터 생성 중 오류가 발생했습니다."
        );
    }

    @PostMapping("/popups")
    public ResponseEntity<?> createPopups(@RequestHeader("X-Conference-Seq") Long conferenceSeq) {
        return execute(() -> testDataService.createPopups(conferenceSeq), "팝업 테스트 데이터 생성 중 오류가 발생했습니다.");
    }

    @PostMapping("/speakers")
    public ResponseEntity<?> createSpeakers(@RequestHeader("X-Conference-Seq") Long conferenceSeq) {
        return execute(() -> testDataService.createSpeakers(conferenceSeq), "초청연자 테스트 데이터 생성 중 오류가 발생했습니다.");
    }

    private ResponseEntity<?> execute(TestDataCreation creation, String errorMessage) {
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
    private interface TestDataCreation {
        TestDataService.CreationResult create();
    }
}
