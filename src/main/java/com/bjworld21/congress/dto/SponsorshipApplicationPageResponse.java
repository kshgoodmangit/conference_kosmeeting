package com.bjworld21.congress.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SponsorshipApplicationPageResponse {
    private List<SponsorshipApplicationResponse> items;
    private int page;
    private int size;
    private long totalCount;
    private long depositedCount;
    private long pendingCount;
    private long totalAmount;
    private int totalPages;
}
