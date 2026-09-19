package com.bjworld21.conference.entity;

import lombok.Data;

@Data
public class AdminAccessRequestMail {
    private Long seq;
    private Long requestSeq;
    private String recipientEmail;
    private int attemptCount;
    private String claimToken;
}
