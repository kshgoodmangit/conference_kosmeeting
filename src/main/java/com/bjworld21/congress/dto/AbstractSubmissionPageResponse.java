package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbstractSubmissionPageResponse {
    private List<AbstractSubmissionResponse> items;
    private Integer page;
    private Integer size;
    private Long totalCount;
    private Integer totalPages;
    private AbstractSubmissionSummary summary;
}
