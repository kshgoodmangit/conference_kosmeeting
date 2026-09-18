package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.CommonCodeResponse;
import com.bjworld21.congress.dto.CommonCodeReorderRequest;
import com.bjworld21.congress.service.CommonCodeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/common-codes")
public class CommonCodeController {
    private final CommonCodeService commonCodeService;

    public CommonCodeController(CommonCodeService commonCodeService) {
        this.commonCodeService = commonCodeService;
    }

    @GetMapping("/tree")
    public ResponseEntity<List<CommonCodeResponse>> getCodeTree() {
        return ResponseEntity.ok(commonCodeService.getCodeTree());
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestParam(defaultValue = "0") Long parentSeq,
            @RequestParam(required = false) String groupCode,
            @RequestParam String codeName,
            @RequestParam(defaultValue = "0") Integer sortOrder,
            @RequestParam(defaultValue = "Y") String isUsed,
            @RequestParam(required = false) String codeEtc1,
            @RequestParam(required = false) String codeEtc2,
            @RequestParam(required = false) String codeEtc3,
            @RequestParam(defaultValue = "Y") String isEditable,
            @RequestParam(defaultValue = "N") String isEtc
    ) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(commonCodeService.create(
                    parentSeq,
                    groupCode,
                    codeName,
                    sortOrder,
                    isUsed,
                    codeEtc1,
                    codeEtc2,
                    codeEtc3,
                    isEditable,
                    isEtc
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("공통코드 등록 중 오류가 발생했습니다.");
        }
    }

    @PutMapping("/{seq}")
    public ResponseEntity<?> update(
            @PathVariable Long seq,
            @RequestParam(required = false) Long parentSeq,
            @RequestParam String codeName,
            @RequestParam(defaultValue = "0") Integer sortOrder,
            @RequestParam(defaultValue = "Y") String isUsed,
            @RequestParam(required = false) String codeEtc1,
            @RequestParam(required = false) String codeEtc2,
            @RequestParam(required = false) String codeEtc3,
            @RequestParam(defaultValue = "Y") String isEditable,
            @RequestParam(defaultValue = "N") String isEtc
    ) {
        try {
            return ResponseEntity.ok(commonCodeService.update(
                    seq,
                    parentSeq,
                    codeName,
                    sortOrder,
                    isUsed,
                    codeEtc1,
                    codeEtc2,
                    codeEtc3,
                    isEditable,
                    isEtc
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("공통코드 저장 중 오류가 발생했습니다.");
        }
    }

    @PutMapping("/reorder")
    public ResponseEntity<?> reorder(@RequestBody CommonCodeReorderRequest request) {
        try {
            return ResponseEntity.ok(commonCodeService.reorder(request != null ? request.getItems() : null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("공통코드 정렬 저장 중 오류가 발생했습니다.");
        }
    }

    @DeleteMapping("/{seq}")
    public ResponseEntity<?> delete(@PathVariable Long seq) {
        try {
            commonCodeService.delete(seq);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("공통코드 삭제 중 오류가 발생했습니다.");
        }
    }
}
