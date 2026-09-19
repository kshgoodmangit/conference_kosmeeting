package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbstractPresentationTypeResponse {
    private Long code;
    private String name;
    private Integer sortOrder;
    private Boolean enabled;
}
