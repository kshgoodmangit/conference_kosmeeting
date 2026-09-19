package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardPostResponse {
    private Long seq;
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
    private List<BoardAttachmentResponse> attachments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
