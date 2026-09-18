package com.bjworld21.congress.service;

import com.bjworld21.congress.config.AdminAccessRequestProperties;
import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.AdminAccessRequestInput;
import com.bjworld21.congress.dto.AdminIpAllowlistRequest;
import com.bjworld21.congress.entity.AdminAccessRequest;
import com.bjworld21.congress.entity.AdminIpAllowlist;
import com.bjworld21.congress.repository.AdminAccessRequestRepository;
import com.bjworld21.congress.repository.AdminAccountRepository;
import com.bjworld21.congress.repository.AdminIpAllowlistRepository;
import com.bjworld21.congress.security.IpCidrRange;
import com.bjworld21.congress.security.RequestSiteUrlResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Service
public class AdminAccessRequestService {
    private final AdminAccessRequestRepository repository;
    private final AdminAccountRepository accounts;
    private final AdminIpAllowlistRepository allowlistRepository;
    private final AdminIpAllowlistService allowlistService;
    private final AdminAccessRequestProperties properties;
    private final PersonalDataProperties personalData;
    private final Clock clock;

    public AdminAccessRequestService(AdminAccessRequestRepository repository, AdminAccountRepository accounts,
            AdminIpAllowlistRepository allowlistRepository, AdminIpAllowlistService allowlistService,
            AdminAccessRequestProperties properties, PersonalDataProperties personalData, Clock clock) {
        this.repository = repository;
        this.accounts = accounts;
        this.allowlistRepository = allowlistRepository;
        this.allowlistService = allowlistService;
        this.properties = properties;
        this.personalData = personalData;
        this.clock = clock;
    }

    @Transactional
    public Submission submit(AdminAccessRequestInput input, String clientIp, String requestSiteUrl) {
        String siteUrl = RequestSiteUrlResolver.normalize(requestSiteUrl);
        String ip = IpCidrRange.parseLiteralAddress(clientIp).getHostAddress();
        LocalDateTime now = LocalDateTime.now(clock);
        validate(input, now.toLocalDate());
        repository.ensureLock(siteUrl, ip);
        repository.lock(siteUrl, ip);
        if (repository.countPending(siteUrl, ip, now) > 0) {
            return new Submission(true, "이미 접수된 요청입니다. 담당자 확인을 기다려 주세요.");
        }
        int cooldown = Math.max(1, properties.getCooldownMinutes());
        int dailyLimit = Math.max(1, properties.getDailyLimit());
        if (repository.countSince(siteUrl, ip, now.minusMinutes(cooldown)) > 0
                || repository.countSince(siteUrl, ip, now.toLocalDate().atStartOfDay()) >= dailyLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "이 IP의 요청 가능 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요.");
        }
        AdminAccessRequest request = new AdminAccessRequest();
        request.setSiteUrl(siteUrl);
        request.setRequestIp(ip);
        request.setAffiliation(input.affiliation().trim());
        request.setRequesterName(input.name().trim());
        request.setContact(input.contact().trim());
        request.setPurpose(input.purpose().trim());
        request.setStartDate(input.startDate());
        request.setEndDate(input.endDate());
        request.setCreatedAt(now);
        request.setExpiresAt(now.plusHours(24));
        repository.insert(request, key());
        accounts.findAllActiveByRole("maintenance", key()).stream()
                .map(account -> account.getContactEmail())
                .filter(java.util.Objects::nonNull)
                .map(email -> email.trim().toLowerCase(java.util.Locale.ROOT))
                .filter(email -> email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")).distinct()
                .forEach(email -> repository.enqueueMail(request.getSeq(), email, key(), now));
        return new Submission(false, "요청이 접수되었습니다. 담당자 확인 후 접근이 허용됩니다.");
    }

    @Transactional(readOnly = true)
    public Page findPage(String keyword, String status, int page, int size) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String normalizedStatus = status == null ? "" : status.trim();
        if (normalizedKeyword.length() > 200 || !Set.of("", "REQUESTED", "APPROVED", "REJECTED", "EXPIRED").contains(normalizedStatus)) {
            throw new IllegalArgumentException("검색 조건을 확인해 주세요.");
        }
        int safePage = Math.max(1, page), safeSize = Math.max(1, Math.min(100, size));
        Map<String,Object> params = Map.of("keyword", normalizedKeyword, "status", normalizedStatus,
                "now", LocalDateTime.now(clock), "key", key(), "size", safeSize, "offset", (long)(safePage - 1) * safeSize);
        return new Page(repository.findPage(params), repository.summary(params), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public AdminAccessRequest findBySeq(Long seq) {
        AdminAccessRequest request = repository.find(seq, key(), LocalDateTime.now(clock));
        if (request == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "요청을 찾을 수 없습니다.");
        return request;
    }

    @Transactional
    public void approve(Long seq, Long adminSeq) {
        AdminAccessRequest request = lockRequest(seq);
        if ("APPROVED".equals(request.getStatus())) return;
        requirePending(request);
        String cidr = IpCidrRange.parse(request.getRequestIp()).canonicalCidr();
        AdminIpAllowlist existing = allowlistRepository.findByIpCidrForUpdate(cidr);
        Long ruleSeq;
        if (existing != null) {
            boolean coversPeriod = Boolean.TRUE.equals(existing.getEnabled())
                    && (existing.getUseStartDate() == null || !existing.getUseStartDate().isAfter(request.getStartDate()))
                    && (existing.getUseEndDate() == null || !existing.getUseEndDate().isBefore(request.getEndDate()));
            if (!coversPeriod) throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "이 IP의 기존 규칙이 요청 기간을 허용하지 않습니다. 접근허용 IP 메뉴에서 기존 규칙을 확인해 주세요.");
            ruleSeq = existing.getSeq();
        } else {
            ruleSeq = allowlistService.create(AdminIpAllowlistRequest.builder()
                    .ruleName("접근 요청 #" + request.getSeq())
                    .ipCidr(request.getRequestIp())
                    .description("접근허용요청 #" + request.getSeq() + " 승인")
                    .useStartDate(request.getStartDate()).useEndDate(request.getEndDate()).enabled(true).build(), adminSeq).getSeq();
        }
        repository.process(seq, "APPROVED", ruleSeq, adminSeq, LocalDateTime.now(clock));
    }

    @Transactional
    public void reject(Long seq, Long adminSeq) {
        AdminAccessRequest request = lockRequest(seq);
        requirePending(request);
        repository.process(seq, "REJECTED", null, adminSeq, LocalDateTime.now(clock));
    }

    private AdminAccessRequest lockRequest(Long seq) {
        if (repository.lockRequest(seq) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "요청을 찾을 수 없습니다.");
        AdminAccessRequest request = repository.find(seq, key(), LocalDateTime.now(clock));
        if (request == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "요청을 찾을 수 없습니다.");
        return request;
    }

    private void requirePending(AdminAccessRequest request) {
        if (!"REQUESTED".equals(request.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리되었거나 만료된 요청입니다.");
    }

    private void validate(AdminAccessRequestInput input, LocalDate today) {
        if (input == null) throw new IllegalArgumentException("요청 정보를 입력해 주세요.");
        required(input.affiliation(), 200); required(input.name(), 100); required(input.contact(), 30); required(input.purpose(), 1000);
        if (!input.contact().trim().matches("^[+0-9\\s().-]+$") || input.contact().replaceAll("\\D", "").length() < 7) {
            throw new IllegalArgumentException("연락 가능한 전화번호를 입력해 주세요.");
        }
        if (input.startDate() == null || input.endDate() == null || input.startDate().isBefore(today)
                || input.endDate().isBefore(input.startDate()) || input.endDate().getYear() > 9999) {
            throw new IllegalArgumentException("사용기간을 확인해 주세요. 시작일은 오늘 이후, 종료일은 시작일 이후여야 합니다.");
        }
    }

    private void required(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException("필수 입력항목과 최대 길이를 확인해 주세요.");
    }
    private String key() { return personalData.requireDbEncString(); }
    public record Submission(boolean duplicate, String message) {}
    public record Page(java.util.List<AdminAccessRequest> items, Map<String,Object> summary, int page, int size) {}
}
