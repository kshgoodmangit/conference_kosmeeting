package com.bjworld21.congress.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 학회 명부는 홈페이지 로그인 회원과 독립적으로 관리한다. */
public final class SocietyMemberData {
    private SocietyMemberData() {}

    @Data
    public static class Member {
        private Long seq;
        private String licenseNumber;
        private String fullName;
        private String affiliation;
        private String memberType;
        private LocalDateTime updatedAt;
    }

    @Data
    public static class Summary {
        private long totalCount;
        private long regularCount;
        private long associateCount;
        private long otherCount;
    }

    public record Page(List<Member> items, Summary summary, int page, int size, int totalPages) {}
    public record RowResult(int rowNumber, String licenseNumber, String status, String message) {}
    public record ImportResult(int totalCount, int successCount, int skippedCount, int failureCount,
                               List<RowResult> results) {}

    @Data
    public static class FeeMapping {
        private String memberType;
        private Long categorySeq;
    }

    @Data
    public static class FeeQuote {
        private String memberType;
        private Long categorySeq;
        private String categoryName;
        private BigDecimal amount;
    }

    public record QuoteRequest(String licenseNumber, String fullName, String periodType, String currency) {}
}
