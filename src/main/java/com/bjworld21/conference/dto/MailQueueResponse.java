package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailQueueResponse {
    private Long jobSeq;
    private String status;
    private int totalCount;
    private int includedCount;
    private int excludedCount;
    private int duplicateCount;
    private int suppressionCount;
    private int invalidCount;
}
