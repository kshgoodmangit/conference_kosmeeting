package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbstractSubmissionAttachmentResponse {
    private Long seq;
    private Long abstractSeq;
    private String originalFilename;
    private String contentType;
    private String fileExtension;
    private Long fileSize;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
