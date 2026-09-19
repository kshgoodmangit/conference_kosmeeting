package com.bjworld21.conference.dto;

import java.util.Map;

public record MailRecipientPreviewResponse(
        Map<String, Integer> groupCounts,
        int includedCount,
        int duplicateCount,
        int suppressionCount,
        int invalidCount
) {
}
