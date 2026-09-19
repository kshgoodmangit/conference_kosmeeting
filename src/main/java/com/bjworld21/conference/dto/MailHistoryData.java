package com.bjworld21.conference.dto;

import com.bjworld21.conference.entity.MailAttachment;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class MailHistoryData {
    private MailHistoryData() {}

    @Data
    public static class SaveRequest {
        @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}")
        private String requestKey;
        @NotBlank @Size(max = 40)
        private String sourceMenu;
        @NotBlank @Size(max = 500)
        private String subject;
        @NotBlank @Size(max = 1_000_000)
        private String htmlContent;
        @NotEmpty @Size(max = 1000)
        private List<@NotNull @Positive Long> sourceSeqs;
    }

    @Data
    public static class Context {
        private Long seq;
        private Long conferenceSeq;
        private Long campaignSeq;
        private Long jobSeq;
        private Long createdBy;
        private String sourceMenu;
        private String requestKey;
        private String requestHash;
        private String adminName;
        private int selectedCount;
        private int duplicateCount;
        private int invalidCount;
        private int suppressionCount;
    }

    @Data
    public static class Source {
        private Long seq;
        private String sourceLabel;
        private String email;
        private String fullName;
        private String affiliation;
    }

    @Data
    public static class Filter {
        private Long conferenceSeq;
        private String keyword = "";
        private String sourceMenu = "";
        private String status = "";
        private LocalDate dateFrom;
        private LocalDate dateTo;
        private String sourceType = "";
        private Long sourceSeq;
        // Set only by the service after a conference-scoped recipient lookup.
        private String exactEmail;
    }

    @Data
    public static class Summary {
        private long totalCount;
        private long savedCount;
        private long recipientCount;
    }

    @Data
    public static class Item {
        private Long seq;
        private Long campaignSeq;
        private String sourceMenu;
        private String subject;
        private String htmlContent;
        private String adminName;
        private Long createdBy;
        private String status;
        private int selectedCount;
        private int duplicateCount;
        private int invalidCount;
        private int suppressionCount;
        private int recipientCount;
        private int excludedCount;
        private LocalDateTime createdAt;
    }

    @Data
    public static class Recipient {
        private Long seq;
        private String email;
        private String fullName;
        private String affiliation;
        private String status;
        private String exclusionReason;
        private LocalDateTime acceptedAt;
        private LocalDateTime deliveredAt;
        private String failureReason;
    }

    @Data
    public static class EmailSummary {
        private long totalCount;
        private long historyCount;
        private long savedCount;
        private long excludedCount;
    }

    @Data
    public static class EmailItem {
        private Long recipientSeq;
        private String email;
        private String fullName;
        private long historyCount;
        private long savedCount;
        private long excludedCount;
        private LocalDateTime lastCreatedAt;
    }

    @Data
    public static class EmailHistoryItem {
        private Long seq;
        private Long recipientSeq;
        private String sourceMenu;
        private String subject;
        private String adminName;
        private String email;
        private String fullName;
        private String status;
        private String exclusionReason;
        private LocalDateTime createdAt;
        private LocalDateTime acceptedAt;
        private LocalDateTime deliveredAt;
        private String failureReason;
    }

    public record Origin(Long recipientSeq, String sourceType, Long sourceSeq, String sourceLabel) {}
    public record Page(List<Item> items, int page, int totalPages, Summary summary) {}
    public record EmailPage(List<EmailItem> items, int page, int totalPages, EmailSummary summary) {}
    public record EmailHistoryPage(String email, List<EmailHistoryItem> items, int page, int totalPages, EmailSummary summary) {}
    public record Detail(Item history, List<Recipient> recipients, List<Origin> origins, List<MailAttachment> attachments) {}
    public record Saved(Long seq, int selectedCount, int duplicateCount, int invalidCount, int suppressionCount) {}
}
