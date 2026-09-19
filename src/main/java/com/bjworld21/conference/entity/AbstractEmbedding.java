package com.bjworld21.conference.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbstractEmbedding {
    private Long seq;
    private Long abstractSeq;
    private String sectionType;
    private String modelName;
    private String modelRevision;
    private Integer dimension;
    private byte[] embedding;
    private String contentHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
