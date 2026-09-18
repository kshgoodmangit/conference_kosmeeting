package com.bjworld21.congress.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MaintenanceRequestAttachmentResponse {
    private Long seq;
    private String attachmentType;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
}
