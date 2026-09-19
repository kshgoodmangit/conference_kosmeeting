package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.AbstractEvaluationItemListResponse;
import com.bjworld21.conference.dto.AbstractEvaluationItemRequest;
import com.bjworld21.conference.service.AbstractEvaluationItemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/admin/abstract-evaluation-items")
public class AbstractEvaluationItemController {
    private final AbstractEvaluationItemService service;

    public AbstractEvaluationItemController(AbstractEvaluationItemService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<AbstractEvaluationItemListResponse> list(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq
    ) {
        return ResponseEntity.ok(service.findAll(conferenceSeq));
    }

    @GetMapping("/used")
    public ResponseEntity<AbstractEvaluationItemListResponse> listUsed(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq
    ) {
        return ResponseEntity.ok(service.findUsed(conferenceSeq));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                    @RequestBody AbstractEvaluationItemRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.create(conferenceSeq, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("평가항목 등록 중 오류가 발생했습니다.");
        }
    }

    @PutMapping("/{seq}")
    public ResponseEntity<?> update(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @PathVariable Long seq,
            @RequestBody AbstractEvaluationItemRequest request
    ) {
        try {
            return ResponseEntity.ok(service.update(conferenceSeq, seq, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("평가항목 수정 중 오류가 발생했습니다.");
        }
    }

    @DeleteMapping("/{seq}")
    public ResponseEntity<?> delete(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                    @PathVariable Long seq) {
        try {
            service.delete(conferenceSeq, seq);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("평가항목 삭제 중 오류가 발생했습니다.");
        }
    }
}
