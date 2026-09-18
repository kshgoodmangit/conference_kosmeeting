package com.bjworld21.congress.entity;

import lombok.Data;

@Data
public class AdminAccessRequestMail {
    private Long seq;
    private Long requestSeq;
    private String recipientEmail;
    private int attemptCount;
    private String claimToken;
}
