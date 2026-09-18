package com.bjworld21.congress.dto;

import lombok.Data;

@Data
public class AbstractSubmissionSummary {
    private long unassignedCount;
    private long pendingReviewCount;
    private long pendingDecisionCount;
    private long acceptedCount;
}
