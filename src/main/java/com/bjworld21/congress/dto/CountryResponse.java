package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountryResponse {
    private Long seq;
    private String isoAlpha2;
    private String isoAlpha3;
    private String isoNumeric;
    private String countryName;
    private String countryNameKo;
    private String dialCode;
    private String isUsed;
}
