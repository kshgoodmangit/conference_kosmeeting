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
public class MailProviderEvent {
    private Long seq;
    private String provider;
    private String providerEventId;
    private String providerMessageId;
    private String eventType;
    private String recipientEmail;
    private LocalDateTime occurredAt;
    private LocalDateTime receivedAt;
    private Boolean signatureVerified;
    private String processStatus;
    private LocalDateTime processedAt;
    private Integer retryCount;
    private String errorMessage;
    private String payloadJson;
}
