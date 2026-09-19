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
public class MailCampaign {
    private Long seq;
    private String subject;
    private String senderName;
    private String senderEmail;
    private String replyToEmail;
    private String htmlContent;
    private String textContent;
    private String mailType;
    private String status;
    private Boolean trackOpens;
    private Boolean trackClicks;
    private LocalDateTime scheduledAt;
    private Integer versionNo;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
