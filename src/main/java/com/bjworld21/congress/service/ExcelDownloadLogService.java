package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.ExcelDownloadLogPageResponse;
import com.bjworld21.congress.entity.ExcelDownloadLog;
import com.bjworld21.congress.repository.ExcelDownloadLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

@Service
public class ExcelDownloadLogService {
    private static final Set<String> STATUSES = Set.of("PROCESSING", "SUCCESS", "FAILED");
    private final ExcelDownloadLogRepository repository;
    private final PersonalDataProperties personalDataProperties;

    public ExcelDownloadLogService(
            ExcelDownloadLogRepository repository,
            PersonalDataProperties personalDataProperties
    ) {
        this.repository = repository;
        this.personalDataProperties = personalDataProperties;
    }

    public ExcelDownloadLogPageResponse findPage(
            Integer page,
            Integer size,
            LocalDate dateFrom,
            LocalDate dateTo,
            String exportType,
            String status,
            String adminKeyword,
            String reasonKeyword
    ) {
        if (dateFrom != null && dateTo != null && dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("조회 종료일은 시작일보다 빠를 수 없습니다.");
        }

        int safePage = page == null ? 1 : Math.max(1, page);
        int safeSize = size == null ? 20 : Math.min(100, Math.max(1, size));
        String normalizedType = normalizeExportType(exportType);
        String normalizedStatus = normalizeStatus(status);
        String normalizedAdminKeyword = trimToNull(adminKeyword);
        String normalizedReasonKeyword = trimToNull(reasonKeyword);
        long totalCount = repository.countPage(
                dateFrom, dateTo, normalizedType, normalizedStatus,
                normalizedAdminKeyword, normalizedReasonKeyword, dbEncString()
        );
        int totalPages = totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / safeSize);
        int adjustedPage = totalPages == 0 ? 1 : Math.min(safePage, totalPages);

        return ExcelDownloadLogPageResponse.builder()
                .items(repository.findPage(
                        dateFrom, dateTo, normalizedType, normalizedStatus,
                        normalizedAdminKeyword, normalizedReasonKeyword,
                        safeSize, (adjustedPage - 1) * safeSize, dbEncString()
                ))
                .page(adjustedPage)
                .size(safeSize)
                .totalCount(totalCount)
                .totalPages(totalPages)
                .build();
    }

    public ExcelDownloadLog findDetail(Long seq) {
        ExcelDownloadLog item = seq == null ? null : repository.findBySeq(seq, dbEncString());
        if (item == null) {
            throw new IllegalArgumentException("다운로드 이력을 찾을 수 없습니다.");
        }
        return item;
    }

    private String normalizeExportType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) return null;
        try {
            return ExcelExportType.valueOf(normalized.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("엑셀 내보내기 유형이 올바르지 않습니다.");
        }
    }

    private String normalizeStatus(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) return null;
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("처리 상태가 올바르지 않습니다.");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
    private String dbEncString() {
        return personalDataProperties.requireDbEncString();
    }
}
