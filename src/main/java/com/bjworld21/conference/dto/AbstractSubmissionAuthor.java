package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbstractSubmissionAuthor {
    private Long seq;
    private Long abstractSeq;
    private Integer authorOrder;
    private String authorName;
    private Integer institutionNo;
    private Boolean isPresentingAuthor;
    private Boolean isCorrespondingAuthor;
    private String email;
    private String country;
    private String officeCountryCode;
    private String officePhoneNumber;
    private String mobileCountryCode;
    private String mobilePhoneNumber;
}
