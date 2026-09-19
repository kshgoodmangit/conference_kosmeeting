package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.AbstractProgramResponse;
import com.bjworld21.conference.entity.ProgramItem;
import com.bjworld21.conference.service.AbstractProgramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/abstract-program")
public class AbstractProgramController {
    private final AbstractProgramService service;

    @GetMapping
    public AbstractProgramResponse getData(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="6") int size, @RequestParam(defaultValue="") String keyword,
            @RequestParam(required=false) Long presentationTypeCode, @RequestParam(required=false) Long categoryCode,
            @RequestParam(defaultValue="free") String assignmentStatus) {
        return service.getData(conferenceSeq, page, size, keyword, presentationTypeCode, categoryCode, assignmentStatus);
    }

    @PostMapping("/items/{seq}/assignment")
    public ProgramItem assign(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                              @PathVariable Long seq, @RequestBody AbstractProgramResponse.AssignRequest request) {
        return service.assign(conferenceSeq, seq, request.abstractSubmissionSeq());
    }

    @DeleteMapping("/items/{seq}/assignment")
    public ProgramItem release(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                               @PathVariable Long seq, @RequestBody AbstractProgramResponse.ReleaseRequest request) {
        return service.release(conferenceSeq, seq, request.abstractSubmissionSeq(), request.restoreOriginal());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(e.getMessage()); }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> conflict(IllegalStateException e) { return ResponseEntity.status(409).body(e.getMessage()); }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<String> concurrent(ConcurrencyFailureException e) {
        return ResponseEntity.status(409).body("다른 관리자가 편성 정보를 변경하고 있습니다. 새로고침 후 다시 시도해주세요.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> error(Exception e) {
        log.error("초록 편성 처리 실패", e);
        return ResponseEntity.internalServerError().body("초록 편성 정보를 처리하지 못했습니다. 잠시 후 다시 시도해주세요.");
    }
}
