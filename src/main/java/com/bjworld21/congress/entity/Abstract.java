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
public class Abstract {
    private Long seq;
    private String title;
    private String author;
    private String affiliation;
    private String filePath;
    private String status; // 상태값: draft, submitted, under_review, approved, rejected
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

