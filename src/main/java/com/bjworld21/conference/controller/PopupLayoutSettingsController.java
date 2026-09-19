package com.bjworld21.conference.controller;

import com.bjworld21.conference.service.PopupLayoutSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/popup-layout-settings")
public class PopupLayoutSettingsController {
    private final PopupLayoutSettingsService popupLayoutSettingsService;

    public PopupLayoutSettingsController(PopupLayoutSettingsService popupLayoutSettingsService) {
        this.popupLayoutSettingsService = popupLayoutSettingsService;
    }

    @GetMapping
    public ResponseEntity<?> getPopupLayoutSettings(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq
    ) {
        try {
            int popupLayoutNo = popupLayoutSettingsService.getPopupLayoutNo(conferenceSeq);
            return ResponseEntity.ok(new PopupLayoutSettingsResponse(conferenceSeq, popupLayoutNo, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("팝업 레이아웃을 불러오는 중 오류가 발생했습니다.");
        }
    }

    @PutMapping
    public ResponseEntity<?> updatePopupLayoutSettings(
            @RequestHeader("X-Conference-Seq") Long conferenceSeq,
            @RequestBody(required = false) PopupLayoutUpdateRequest request
    ) {
        try {
            Integer requestedLayoutNo = request == null ? null : request.popupLayoutNo();
            int popupLayoutNo = popupLayoutSettingsService.updatePopupLayoutNo(conferenceSeq, requestedLayoutNo);
            return ResponseEntity.ok(new PopupLayoutSettingsResponse(
                    conferenceSeq,
                    popupLayoutNo,
                    "팝업 레이아웃을 변경했습니다."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("팝업 레이아웃을 변경하는 중 오류가 발생했습니다.");
        }
    }

    public record PopupLayoutUpdateRequest(Integer popupLayoutNo) {
    }

    public record PopupLayoutSettingsResponse(Long conferenceSeq, int popupLayoutNo, String message) {
    }
}
