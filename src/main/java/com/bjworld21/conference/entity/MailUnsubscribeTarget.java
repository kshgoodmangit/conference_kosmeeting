package com.bjworld21.conference.entity;

import lombok.Data;

@Data
public class MailUnsubscribeTarget {
    private Long recipientSeq;
    private Long campaignSeq;
    private String email;
    private String normalizedEmail;
}
