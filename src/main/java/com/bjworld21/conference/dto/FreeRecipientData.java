package com.bjworld21.conference.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/** 회원 계정과 독립적인 무료 지정 명단. seq는 추후 자격 연결의 식별자다. */
public final class FreeRecipientData {
    private FreeRecipientData() {}
    @Data
    public static class Recipient {
        private Long seq;
        private String affiliation;
        private String fullName;
        private String position;
        private String phoneNumber;
        private String normalizedPhone;
        private String email;
        private String recipientType;
        private String isUsed;
        private String adminMemo;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
    @Data
    public static class Summary {
        private long totalCount;
        private long activeCount;
        private long inactiveCount;
    }
    public record Page(List<Recipient> items, Summary summary, int page, int size, int totalPages) {}
    public record RowResult(int rowNumber, String fullName, String status, String message) {}
    public record ImportResult(int totalCount, int successCount, int skippedCount, int failureCount,
                               List<RowResult> results) {}
}
