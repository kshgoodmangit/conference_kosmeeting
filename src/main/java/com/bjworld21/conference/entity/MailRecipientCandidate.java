package com.bjworld21.conference.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailRecipientCandidate {
    private Long contactSeq;
    private String email;
    private String normalizedEmail;
    private String fullName;
    private String affiliation;
}
