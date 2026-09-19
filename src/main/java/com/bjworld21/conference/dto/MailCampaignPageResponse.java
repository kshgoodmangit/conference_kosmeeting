package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailCampaignPageResponse {
    private List<MailCampaignSummaryResponse> items;
    private int page;
    private int size;
    private long totalCount;
    private int totalPages;
}
