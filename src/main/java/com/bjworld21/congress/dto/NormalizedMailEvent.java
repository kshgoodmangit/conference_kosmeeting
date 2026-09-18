package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormalizedMailEvent {
    private String provider;
    private String providerEventId;
    private String providerMessageId;
    private String eventType;
    private String recipientEmail;
    private LocalDateTime occurredAt;
    private String clickedUrl;
    private String bounceType;
    private String failureCode;
    private String failureReason;
    private String metadataJson;
    private String payloadJson;
}
