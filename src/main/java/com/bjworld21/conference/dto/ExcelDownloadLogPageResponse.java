package com.bjworld21.conference.dto;

import com.bjworld21.conference.entity.ExcelDownloadLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelDownloadLogPageResponse {
    private List<ExcelDownloadLog> items;
    private int page;
    private int size;
    private long totalCount;
    private int totalPages;
}
