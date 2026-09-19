package com.bjworld21.conference.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MaintenanceRequestAttachment {
    private Long seq;
    private Long maintenanceRequestSeq;
    private String attachmentType;
    private String originalFilename;
    private String savedFilename;
    private String contentType;
    private String fileExtension;
    private Long fileSize;
    private Integer sortOrder;
    private Long uploadedByAdminSeq;
    private LocalDateTime createdAt;
}
