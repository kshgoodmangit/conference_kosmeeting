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
public class BoardAttachment {
    private Long seq;
    private Long boardPostSeq;
    private String originalFilename;
    private String savedFilename;
    private String contentType;
    private Long fileSize;
    private Integer sortOrder;
    private Long downloadCount;
    private Long createdBy;
    private LocalDateTime createdAt;
}
