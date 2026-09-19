package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailCampaignSummaryResponse {
    private Long seq;
    private String subject;
    private String senderName;
    private String senderEmail;
    private String mailType;
    private String status;
    private LocalDateTime scheduledAt;
    private Integer sourceCount;
    private Integer attachmentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
