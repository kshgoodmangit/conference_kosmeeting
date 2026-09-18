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
public class SponsorPageResponse {
    private List<SponsorResponse> items;
    private int page;
    private int size;
    private long totalCount;
    private long enabledCount;
    private int totalPages;
}
