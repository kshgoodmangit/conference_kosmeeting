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
public class BoardAttachmentResponse {
    private Long seq;
    private Long boardPostSeq;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private Integer sortOrder;
    private Long downloadCount;
    private String downloadUrl;
    private LocalDateTime createdAt;
}
