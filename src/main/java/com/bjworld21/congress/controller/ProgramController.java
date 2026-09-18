package com.bjworld21.congress.controller;

import com.bjworld21.congress.dto.ProgramDayRequest;
import com.bjworld21.congress.dto.ProgramDayRoomsRequest;
import com.bjworld21.congress.dto.ProgramItemRequest;
import com.bjworld21.congress.dto.ProgramManagementResponse;
import com.bjworld21.congress.dto.ProgramRoomRequest;
import com.bjworld21.congress.service.ProgramBulkImportService;
import com.bjworld21.congress.service.ProgramService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/program")
public class ProgramController {
    private final ProgramService programService;
    private final ProgramBulkImportService programBulkImportService;

    public ProgramController(ProgramService programService, ProgramBulkImportService programBulkImportService) {
        this.programService = programService;
        this.programBulkImportService = programBulkImportService;
    }

    @GetMapping
    public ResponseEntity<ProgramManagementResponse> getManagementData(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) Boolean enabled
    ) {
        return ResponseEntity.ok(programService.searchManagementData(conferenceSeq, keyword, itemType, enabled));
    }

    @PostMapping("/days")
    public ResponseEntity<?> createDay(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestBody ProgramDayRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(programService.createDay(conferenceSeq, request));
    }

    @PutMapping("/days/{seq}")
    public ResponseEntity<?> updateDay(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                       @PathVariable Long seq, @RequestBody ProgramDayRequest request) {
        return ResponseEntity.ok(programService.updateDay(conferenceSeq, seq, request));
    }

    @DeleteMapping("/days/{seq}")
    public ResponseEntity<?> deleteDay(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                       @PathVariable Long seq) {
        programService.deleteDay(conferenceSeq, seq);
        return ResponseEntity.ok("프로그램 일자를 삭제했습니다.");
    }

    @PutMapping("/days/{seq}/rooms")
    public ResponseEntity<?> replaceDayRooms(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                             @PathVariable Long seq, @RequestBody ProgramDayRoomsRequest request) {
        return ResponseEntity.ok(programService.replaceDayRooms(
                conferenceSeq, seq, request == null ? null : request.getRooms()
        ));
    }

    @PostMapping("/rooms")
    public ResponseEntity<?> createRoom(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                        @RequestBody ProgramRoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(programService.createRoom(conferenceSeq, request));
    }

    @PutMapping("/rooms/{seq}")
    public ResponseEntity<?> updateRoom(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                        @PathVariable Long seq, @RequestBody ProgramRoomRequest request) {
        return ResponseEntity.ok(programService.updateRoom(conferenceSeq, seq, request));
    }

    @DeleteMapping("/rooms/{seq}")
    public ResponseEntity<?> deleteRoom(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                        @PathVariable Long seq) {
        programService.deleteRoom(conferenceSeq, seq);
        return ResponseEntity.ok("프로그램 룸을 삭제했습니다.");
    }

    @PostMapping("/items")
    public ResponseEntity<?> createItem(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                        @RequestBody ProgramItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(programService.createItem(conferenceSeq, request));
    }

    @PostMapping(value = "/items/bulk-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> bulkImportItems(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                             @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(programBulkImportService.importItems(conferenceSeq, file));
    }

    @PutMapping("/items/{seq}")
    public ResponseEntity<?> updateItem(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                        @PathVariable Long seq, @RequestBody ProgramItemRequest request) {
        return ResponseEntity.ok(programService.updateItem(conferenceSeq, seq, request));
    }

    @DeleteMapping("/items/{seq}")
    public ResponseEntity<?> deleteItem(@RequestHeader("X-Conference-Seq") Long conferenceSeq,
                                        @PathVariable Long seq) {
        programService.deleteItem(conferenceSeq, seq);
        return ResponseEntity.ok("프로그램 항목을 삭제했습니다.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("행사 프로그램 처리 중 오류가 발생했습니다.");
    }
}
