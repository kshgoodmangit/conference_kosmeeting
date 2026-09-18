package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbstractCategoryResponse {
    private Long code;
    private String name;
    private Integer sortOrder;
    private Boolean enabled;
}
