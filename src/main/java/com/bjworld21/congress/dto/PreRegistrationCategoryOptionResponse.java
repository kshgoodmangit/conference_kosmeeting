package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreRegistrationCategoryOptionResponse {
    private Long categorySeq;
    private String categoryCode;
    private String categoryName;
}
