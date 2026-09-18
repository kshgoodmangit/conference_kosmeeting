package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PopupResponse {
    private Long seq;
    private String title;
    private String linkUrl;
    private Boolean enabled;
    private LocalDate useStartDate;
    private LocalDate useEndDate;
    private String content;
    private String popupImageOriFilename;
    private String popupImageSaveFilename;
    private String imageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String webRiskWarning;
}
