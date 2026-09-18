package com.bjworld21.congress.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MaintenanceRequest {
    private Long seq;
    private Long conferenceSeq;
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
    private String isDelete;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int attachmentCount;
}
