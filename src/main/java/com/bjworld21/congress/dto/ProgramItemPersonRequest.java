package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgramItemPersonRequest {
    private String roleType;
    private Long countrySeq;
    private String affiliation;
    private String personName;
    private Integer sortOrder;
    private Boolean enabled;
}
