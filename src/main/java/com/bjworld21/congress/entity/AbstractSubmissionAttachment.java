package com.bjworld21.congress.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbstractSubmissionAttachment {
    private Long seq;
    private Long abstractSeq;
    private String originalFilename;
    private String saveFilename;
    private String contentType;
    private String fileExtension;
    private Long fileSize;
    private String checksumSha256;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
