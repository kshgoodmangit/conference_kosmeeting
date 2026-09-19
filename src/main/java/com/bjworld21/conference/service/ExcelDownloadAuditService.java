package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.config.AdminRolePolicy;
import com.bjworld21.conference.entity.AdminAccount;
import com.bjworld21.conference.entity.ExcelDownloadLog;
import com.bjworld21.conference.repository.AdminAccountRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Supplier;

@Service
public class ExcelDownloadAuditService {
    private static final Logger log = LoggerFactory.getLogger(ExcelDownloadAuditService.class);
    private static final int MAX_REASON_LENGTH = 500;
    private static final int MAX_IP_LENGTH = 45;
    private static final int MAX_USER_AGENT_LENGTH = 500;

    private final ExcelDownloadAuditLogWriter writer;
    private final AdminAccountRepository adminAccountRepository;
    private final ObjectMapper objectMapper;
    private final PersonalDataProperties personalDataProperties;

    public ExcelDownloadAuditService(
            ExcelDownloadAuditLogWriter writer,
            AdminAccountRepository adminAccountRepository,
            ObjectMapper objectMapper,
            PersonalDataProperties personalDataProperties
    ) {
        this.writer = writer;
        this.adminAccountRepository = adminAccountRepository;
        this.objectMapper = objectMapper;
        this.personalDataProperties = personalDataProperties;
    }

    public AuditedExcelExportResult execute(
            ExcelExportType exportType,
            Long adminSeq,
            String reason,
            Map<String, Object> filters,
            HttpServletRequest request,
            Supplier<ExcelExportResult> generator
    ) {
        String normalizedReason = normalizeReason(reason);
        AdminAccount admin = requireActiveAdmin(adminSeq);
        String filterJson = serializeFilters(filters);

        ExcelDownloadLog auditLog = ExcelDownloadLog.builder()
                .exportType(exportType.name())
                .menuKey(exportType.getMenuKey())
                .menuName(exportType.getMenuName())
                .reason(normalizedReason)
                .filterJson(filterJson)
                .adminSeq(admin.getSeq())
                .adminEmail(admin.getEmail())
                .adminName(admin.getAdminName())
                .adminRole(admin.getRole())
                .ipAddress(limit(trimToNull(request.getRemoteAddr()), MAX_IP_LENGTH))
                .userAgent(limit(trimToNull(request.getHeader("User-Agent")), MAX_USER_AGENT_LENGTH))
                .status("PROCESSING")
                .build();

        long logSeq = writer.start(auditLog);
        try {
            ExcelExportResult result = ExcelDownloadTrace.attach(generator.get(), logSeq, exportType);
            writer.succeed(logSeq, result);
            return new AuditedExcelExportResult(logSeq, result);
        } catch (RuntimeException exception) {
            try {
                writer.fail(logSeq, "EXPORT_FAILED", "엑셀 파일 생성 중 오류가 발생했습니다.");
            } catch (RuntimeException auditException) {
                log.error("Failed to update excel download audit log: seq={}", logSeq, auditException);
            }
            throw exception;
        }
    }

    private String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 5) {
            throw new IllegalArgumentException("다운로드 사유를 5자 이상 입력해 주세요.");
        }
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("다운로드 사유는 500자 이하로 입력해 주세요.");
        }
        return normalized;
    }

    private AdminAccount requireActiveAdmin(Long adminSeq) {
        AdminAccount admin = adminSeq == null ? null : adminAccountRepository.findBySeq(
                adminSeq, personalDataProperties.requireDbEncString()
        );
        if (admin == null || !"active".equals(admin.getStatus())
                || !AdminRolePolicy.isFullAdministrator(admin.getRole())) {
            throw new IllegalStateException("유효한 관리자 계정 정보를 확인할 수 없습니다.");
        }
        return admin;
    }

    private String serializeFilters(Map<String, Object> filters) {
        try {
            return objectMapper.writeValueAsString(filters == null ? Map.of() : filters);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("엑셀 조회 조건을 기록하지 못했습니다.", exception);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String limit(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
