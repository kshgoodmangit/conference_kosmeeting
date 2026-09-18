package com.bjworld21.congress.dto;

import java.util.List;

public record MailAddressBookImportResponse(
        int totalRows,
        int importedCount,
        int createdCount,
        int updatedCount,
        int duplicateCount,
        int errorCount,
        List<RowError> errors
) {
    public record RowError(int rowNumber, String message) {
    }
}
