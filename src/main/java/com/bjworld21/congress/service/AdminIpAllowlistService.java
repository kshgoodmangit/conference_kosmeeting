package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.AdminIpAllowlistRequest;
import com.bjworld21.congress.dto.AdminIpAllowlistResponse;
import com.bjworld21.congress.entity.AdminIpAllowlist;
import com.bjworld21.congress.repository.AdminIpAllowlistRepository;
import com.bjworld21.congress.security.IpCidrRange;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class AdminIpAllowlistService {
    private final AdminIpAllowlistRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final PersonalDataProperties personalDataProperties;

    public AdminIpAllowlistService(
            AdminIpAllowlistRepository repository,
            ApplicationEventPublisher eventPublisher,
            PersonalDataProperties personalDataProperties
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.personalDataProperties = personalDataProperties;
    }

    public List<AdminIpAllowlistResponse> findAll() {
        return repository.findAll(dbEncString()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public AdminIpAllowlistResponse create(AdminIpAllowlistRequest request, Long adminSeq) {
        ValidatedRule validated = validate(request, null);
        AdminIpAllowlist rule = AdminIpAllowlist.builder()
                .ruleName(validated.ruleName())
                .ipCidr(validated.ipCidr())
                .description(validated.description())
                .useStartDate(validated.useStartDate())
                .useEndDate(validated.useEndDate())
                .enabled(validated.enabled())
                .createdByAdminSeq(adminSeq)
                .updatedByAdminSeq(adminSeq)
                .build();
        repository.insert(rule);
        eventPublisher.publishEvent(new AdminIpAllowlistChangedEvent(rule.getSeq(), "CREATE"));
        return toResponse(repository.findBySeq(rule.getSeq(), dbEncString()));
    }

    @Transactional
    public AdminIpAllowlistResponse update(Long seq, AdminIpAllowlistRequest request, Long adminSeq) {
        if (repository.findBySeq(seq, dbEncString()) == null) {
            throw new IllegalArgumentException("접근 허용 IP 규칙을 찾을 수 없습니다.");
        }
        ValidatedRule validated = validate(request, seq);
        AdminIpAllowlist rule = AdminIpAllowlist.builder()
                .seq(seq)
                .ruleName(validated.ruleName())
                .ipCidr(validated.ipCidr())
                .description(validated.description())
                .useStartDate(validated.useStartDate())
                .useEndDate(validated.useEndDate())
                .enabled(validated.enabled())
                .updatedByAdminSeq(adminSeq)
                .build();
        if (repository.update(rule) == 0) {
            throw new IllegalArgumentException("접근 허용 IP 규칙을 찾을 수 없습니다.");
        }
        eventPublisher.publishEvent(new AdminIpAllowlistChangedEvent(seq, "UPDATE"));
        return toResponse(repository.findBySeq(seq, dbEncString()));
    }

    @Transactional
    public void delete(Long seq) {
        if (repository.delete(seq) == 0) {
            throw new IllegalArgumentException("접근 허용 IP 규칙을 찾을 수 없습니다.");
        }
        eventPublisher.publishEvent(new AdminIpAllowlistChangedEvent(seq, "DELETE"));
    }

    private ValidatedRule validate(AdminIpAllowlistRequest request, Long excludeSeq) {
        if (request == null) {
            throw new IllegalArgumentException("접근 허용 IP 정보가 필요합니다.");
        }
        String ruleName = normalizeRequired(request.getRuleName(), 100, "규칙명은 필수입니다.", "규칙명은 100자 이내로 입력해 주세요.");
        String ipCidr = IpCidrRange.parse(request.getIpCidr()).canonicalCidr();
        String description = normalizeOptional(request.getDescription(), 500, "설명은 500자 이내로 입력해 주세요.");
        LocalDate useStartDate = request.getUseStartDate();
        LocalDate useEndDate = request.getUseEndDate();
        if (useStartDate != null && useEndDate != null && useEndDate.isBefore(useStartDate)) {
            throw new IllegalArgumentException("사용 종료일은 시작일보다 빠를 수 없습니다.");
        }
        if (repository.countByIpCidrExcludingSeq(ipCidr, excludeSeq) > 0) {
            throw new IllegalArgumentException("이미 등록된 IP 또는 CIDR입니다.");
        }
        return new ValidatedRule(
                ruleName,
                ipCidr,
                description,
                useStartDate,
                useEndDate,
                request.getEnabled() != null ? request.getEnabled() : Boolean.TRUE
        );
    }

    private AdminIpAllowlistResponse toResponse(AdminIpAllowlist rule) {
        LocalDate today = LocalDate.now();
        boolean activeNow = Boolean.TRUE.equals(rule.getEnabled())
                && (rule.getUseStartDate() == null || !rule.getUseStartDate().isAfter(today))
                && (rule.getUseEndDate() == null || !rule.getUseEndDate().isBefore(today));
        return AdminIpAllowlistResponse.builder()
                .seq(rule.getSeq())
                .ruleName(rule.getRuleName())
                .ipCidr(rule.getIpCidr())
                .description(rule.getDescription())
                .useStartDate(rule.getUseStartDate())
                .useEndDate(rule.getUseEndDate())
                .enabled(rule.getEnabled())
                .activeNow(activeNow)
                .createdByAdminSeq(rule.getCreatedByAdminSeq())
                .updatedByAdminSeq(rule.getUpdatedByAdminSeq())
                .createdByAdminName(rule.getCreatedByAdminName())
                .updatedByAdminName(rule.getUpdatedByAdminName())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    private String normalizeRequired(String value, int maxLength, String requiredMessage, String lengthMessage) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(requiredMessage);
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(lengthMessage);
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength, String lengthMessage) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(lengthMessage);
        }
        return normalized;
    }

    private record ValidatedRule(
            String ruleName,
            String ipCidr,
            String description,
            LocalDate useStartDate,
            LocalDate useEndDate,
            Boolean enabled
    ) {
    }
    private String dbEncString() {
        return personalDataProperties.requireDbEncString();
    }
}
