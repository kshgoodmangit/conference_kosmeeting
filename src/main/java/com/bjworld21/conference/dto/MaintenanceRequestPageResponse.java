package com.bjworld21.conference.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MaintenanceRequestPageResponse {
    private List<MaintenanceRequestResponse> items;
    private int page;
    private int size;
    private long totalCount;
    private long requestedCount;
    private long inProgressCount;
    private long completedCount;
}
