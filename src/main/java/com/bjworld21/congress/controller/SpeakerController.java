package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.SpeakerPageResponse;
import com.bjworld21.congress.dto.SpeakerRequest;
import com.bjworld21.congress.dto.SpeakerResponse;
import com.bjworld21.congress.service.SpeakerService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/speakers")
public class SpeakerController {
    private static final Logger log = LoggerFactory.getLogger(SpeakerController.class);
    private final SpeakerService service;

    public SpeakerController(SpeakerService service) { this.service = service; }

    @GetMapping
    public SpeakerPageResponse list(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "10") int size,
                                    @RequestParam(defaultValue = "") String keyword,
                                    @RequestParam(required = false) Long typeCode,
                                    @RequestParam(required = false) Boolean enabled) {
        return service.list(conferenceSeq, page, size, keyword, typeCode, enabled);
    }

    @GetMapping("/types")
    public List<SpeakerService.SpeakerType> types() { return service.types(); }

    @GetMapping("/{seq}")
    public SpeakerResponse get(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                               @PathVariable Long seq) { return service.get(conferenceSeq, seq); }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SpeakerResponse> create(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                                 @Valid @ModelAttribute SpeakerRequest request,
                                                 @RequestParam(required = false) MultipartFile profileImage) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(conferenceSeq, request, profileImage));
    }

    @PutMapping(value = "/{seq}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SpeakerResponse update(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                  @PathVariable Long seq, @Valid @ModelAttribute SpeakerRequest request,
                                  @RequestParam(required = false) MultipartFile profileImage) {
        return service.update(conferenceSeq, seq, request, profileImage);
    }

    @DeleteMapping("/{seq}")
    public ResponseEntity<Void> delete(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                       @PathVariable Long seq) {
        service.delete(conferenceSeq, seq);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{seq}/image")
    public ResponseEntity<Resource> image(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                          @PathVariable Long seq) {
        Resource resource = service.image(conferenceSeq, seq);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM)).body(resource);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<String> validation(BindException exception) {
        return ResponseEntity.badRequest().body(exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.isBindingFailure() ? "입력값 형식을 확인해주세요." : error.getDefaultMessage())
                .findFirst().orElse("입력값을 확인해주세요."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> status(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(exception.getReason());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> conflict(DataIntegrityViolationException exception) {
        log.warn("연자 데이터 제약조건 충돌", exception);
        return ResponseEntity.status(HttpStatus.CONFLICT).body("데이터가 변경되었거나 사용 중입니다. 새로고침 후 다시 시도해주세요.");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> failure(IllegalStateException exception) {
        log.error("연자 파일 처리 실패", exception);
        return ResponseEntity.internalServerError().body("파일 처리 중 오류가 발생했습니다. 다시 시도해주세요.");
    }
}
