package com.bjworld21.conference.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class MaintenanceRequestResponse {
    private Long seq;
    private String title;
    private String content;
    private String status;
    private Long requestedByAdminSeq;
    private String requestedByName;
    private Long assignedToAdminSeq;
    private String assignedToName;
    private String answerContent;
    private Long answeredByAdminSeq;
    private String answeredByName;
    private LocalDateTime answeredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int attachmentCount;
    private List<MaintenanceRequestAttachmentResponse> attachments;
}
