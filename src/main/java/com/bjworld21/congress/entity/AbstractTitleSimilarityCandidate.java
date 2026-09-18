package com.bjworld21.congress.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbstractTitleSimilarityCandidate {
    private Long seq;
    private String submissionNo;
    private String title;
    private String status;
}
