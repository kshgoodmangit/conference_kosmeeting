package com.bjworld21.conference.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SmsData {
    private SmsData() {}
    @Data
    public static class DirectRecipient {
        @NotBlank @Size(max = 50) private String phoneNumber;
        @Size(max = 255) private String fullName;
    }
    @Data
    public static class Selection {
        @Size(max = 4)
        private List<@NotNull @Pattern(regexp = "ALL_MEMBERS|ALL_REGISTRANTS|ALL_SUBMITTERS|ALL_ACCEPTED") String> recipientGroups = new ArrayList<>();
        @Size(max = 1000) private List<@NotNull @Positive Long> addressBookSeqs = new ArrayList<>();
        @Valid @Size(max = 10000) private List<@NotNull DirectRecipient> directRecipients = new ArrayList<>();
    }
    @Data @EqualsAndHashCode(callSuper = true)
    public static class CampaignRequest extends Selection {
        @NotBlank(message = "문자 제목을 입력해 주세요.") @Size(max = 100) private String title;
        @NotBlank(message = "발신번호를 입력해 주세요.") @Size(max = 30) private String senderNumber;
        @NotBlank(message = "문자 내용을 입력해 주세요.") @Size(max = 2000) private String message;
        @NotNull @Pattern(regexp = "AUTO|SMS|LMS") private String messageType = "AUTO";
        private LocalDateTime scheduledAt;
        @Min(0) private Integer versionNo;
    }
    @Data
    public static class PrepareRequest {
        @NotBlank @Size(max = 100) private String idempotencyKey;
    }
    @Data
    public static class Campaign {
        private Long seq;
        private String title;
        private String senderNumber;
        private String message;
        private String messageType;
        private String status;
        private LocalDateTime scheduledAt;
        private Integer versionNo;
        private Long createdBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private int sourceCount;
        private Integer includedCount;
        private Integer excludedCount;
    }
    @Data
    public static class Candidate {
        private Long memberSeq;
        private String phoneNumber;
        private String fullName;
    }
    @Data
    public static class AddressBook {
        private Long seq;
        private String addressBookName;
        private int contactCount;
    }
    @Data
    public static class Source {
        private String sourceType;
        private String groupCode;
        private Long addressBookSeq;
        private String phoneNumber;
        private String fullName;
    }
    @Data
    public static class Job {
        private Long seq;
        private Long campaignSeq;
        private String idempotencyKey;
        private String provider;
        private String status;
        private int includedCount;
        private int excludedCount;
        private int duplicateCount;
        private int invalidCount;
        private int suppressionCount;
        private LocalDateTime scheduledAt;
        private LocalDateTime createdAt;
    }
    @Data
    public static class Recipient {
        private Long seq;
        private String phoneNumber;
        private String normalizedPhone;
        private String fullName;
        private String status;
        private String exclusionReason;
        private String providerMessageId;
        private String errorMessage;
    }
    @Data
    public static class Summary {
        private long totalCount;
        private long draftCount;
        private long preparedCount;
    }
    public record Page(List<Campaign> items, int page, int totalPages, Summary summary) {}
    public record Detail(Campaign campaign, Selection selection, Job job) {}
    public record RecipientPage(List<Recipient> items, int page, int totalPages, long totalCount) {}
    public record GroupCounts(Map<String, Integer> groupCounts) {}
}
