package com.bjworld21.congress.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PreRegistrationCreateRequest extends PreRegistrationUpdateRequest {
    private Long memberSeq;
    private Long conferenceSeq;
    private String societyLicenseNumber;
    private String societyMemberName;
}
