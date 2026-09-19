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
public class BoardPostRequest {
    private Long categorySeq;
    private String title;
    private String content;
    private String status;
    private Boolean isPinned;
    private Integer sortOrder;
    private LocalDateTime publishedAt;
    private LocalDateTime publishEndAt;
}
