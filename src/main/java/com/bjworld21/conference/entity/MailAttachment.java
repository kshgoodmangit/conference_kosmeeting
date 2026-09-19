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
public class MailAttachment {
    private Long seq;
    private Long campaignSeq;
    private String originalFilename;
    private String savedFilename;
    private String contentType;
    private Long fileSize;
    private LocalDateTime createdAt;
}
