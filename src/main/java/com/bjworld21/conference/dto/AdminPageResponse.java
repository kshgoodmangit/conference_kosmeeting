package com.bjworld21.conference.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminPageResponse {
    private List<AdminResponse> items;
    private int page;
    private int size;
    private long totalCount;
    private long adminCount;
    private long reviewerCount;
    private long maintenanceCount;
    private int totalPages;
}
