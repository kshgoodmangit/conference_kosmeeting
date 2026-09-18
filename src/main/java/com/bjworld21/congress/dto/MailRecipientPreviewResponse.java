package com.bjworld21.congress.dto;

import java.util.Map;

public record MailRecipientPreviewResponse(
        Map<String, Integer> groupCounts,
        int includedCount,
        int duplicateCount,
        int suppressionCount,
        int invalidCount
) {
}
