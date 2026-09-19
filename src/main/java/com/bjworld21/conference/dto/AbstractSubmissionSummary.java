package com.bjworld21.conference.dto;

import lombok.Data;

@Data
public class AbstractSubmissionSummary {
    private long unassignedCount;
    private long pendingReviewCount;
    private long pendingDecisionCount;
    private long acceptedCount;
}
