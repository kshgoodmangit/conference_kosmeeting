package com.bjworld21.conference.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardPost {
    private Long seq;
    private Long conferenceSeq;
    private Long boardSeq;
    private Long categorySeq;
    private String categoryName;
    private String categoryCode;
    private String title;
    private String content;
    private String status;
    private Boolean isPinned;
    private Integer sortOrder;
    private Long viewCount;
    private LocalDateTime publishedAt;
    private LocalDateTime publishEndAt;
    private Long createdBy;
    private String createdByName;
    private Long updatedBy;
    private String updatedByName;
    private Integer attachmentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
