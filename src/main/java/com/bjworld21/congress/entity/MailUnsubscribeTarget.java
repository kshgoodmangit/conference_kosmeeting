package com.bjworld21.congress.entity;

import lombok.Data;

@Data
public class MailUnsubscribeTarget {
    private Long recipientSeq;
    private Long campaignSeq;
    private String email;
    private String normalizedEmail;
}
