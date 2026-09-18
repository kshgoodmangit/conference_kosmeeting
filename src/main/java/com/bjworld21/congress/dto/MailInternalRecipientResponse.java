package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MailInternalRecipientResponse {
    private String recipientType;
    private Long referenceSeq;
    private String email;
    private String fullName;
    private String affiliation;
    private String detail;
}
