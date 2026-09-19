package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.dto.MailHistoryData.*;
import com.bjworld21.conference.entity.*;
import com.bjworld21.conference.repository.MailCampaignRepository;
import com.bjworld21.conference.repository.MailHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class MailHistoryService {
    private static final Set<String> MENUS = Set.of("pre-registrations", "abstracts", "oral-accepted-abstracts",
            "poster-accepted-abstracts", "speakers", "sponsorship");
    private static final Set<String> EXTENSIONS = Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "zip", "png", "jpg", "jpeg", "gif", "webp", "txt");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final MailHistoryRepository histories;
    private final MailCampaignRepository campaigns;
    private final PersonalDataProperties personalData;
    private final MailHtmlSanitizer sanitizer;
    private final UploadStorage storage;
    private final ObjectMapper json;

    public MailHistoryService(MailHistoryRepository histories, MailCampaignRepository campaigns,
            PersonalDataProperties personalData, MailHtmlSanitizer sanitizer, UploadStorage storage, ObjectMapper json) {
        this.histories = histories;
        this.campaigns = campaigns;
        this.personalData = personalData;
        this.sanitizer = sanitizer;
        this.storage = storage;
        this.json = json;
    }

    @Transactional
    public Saved save(Long conferenceSeq, Long adminSeq, SaveRequest request, List<MultipartFile> uploads) {
        if (conferenceSeq == null || conferenceSeq <= 0 || adminSeq == null || adminSeq <= 0) {
            throw new IllegalArgumentException("학회와 관리자 정보가 필요합니다.");
        }
        if (!MENUS.contains(request.getSourceMenu())) throw new IllegalArgumentException("지원하지 않는 발송 메뉴입니다.");
        if (request.getSourceSeqs() == null || request.getSourceSeqs().isEmpty() || request.getSourceSeqs().size() > 1000
                || request.getSourceSeqs().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("수신자는 1~1,000개 항목을 선택해 주세요.");
        }
        String requestKey;
        try { requestKey = UUID.fromString(request.getRequestKey()).toString(); }
        catch (RuntimeException e) { throw new IllegalArgumentException("저장 요청 키가 올바르지 않습니다."); }
        String subject = Objects.toString(request.getSubject(), "").trim();
        if (subject.isEmpty() || subject.length() > 500) throw new IllegalArgumentException("제목은 1~500자로 입력해 주세요.");
        String rawHtml = Objects.toString(request.getHtmlContent(), "");
        if (rawHtml.length() > 1_000_000) throw new IllegalArgumentException("메일 본문이 너무 큽니다.");
        String html = sanitizer.sanitize(rawHtml);
        if (!sanitizer.hasVisibleContent(html)) throw new IllegalArgumentException("메일 내용을 입력해 주세요.");
        List<Long> ids = request.getSourceSeqs().stream().distinct().sorted().toList();
        List<MultipartFile> files = uploads == null ? List.of() : uploads;
        validateFiles(files);
        String key = personalData.requireDbEncString();
        Context context = new Context();
        context.setConferenceSeq(conferenceSeq);
        context.setCreatedBy(adminSeq);
        context.setSourceMenu(request.getSourceMenu());
        context.setRequestKey(requestKey);
        context.setRequestHash(fingerprint(request.getSourceMenu(), subject, html, ids, files));
        context.setAdminName(Objects.toString(histories.adminName(adminSeq, key), "관리자 #" + adminSeq));
        histories.reserve(context, key);
        Context locked = histories.lockRequest(conferenceSeq, adminSeq, requestKey);
        if (!context.getRequestHash().equals(locked.getRequestHash())) {
            throw new IllegalStateException("동일한 요청 키로 다른 내용을 저장할 수 없습니다. 작성 화면을 다시 열어 주세요.");
        }
        if (locked.getJobSeq() != null) return saved(locked);
        context.setSeq(locked.getSeq());

        List<Source> sources = histories.sources(conferenceSeq, request.getSourceMenu(), ids, key);
        if (sources.size() != ids.size()) throw new IllegalArgumentException("삭제되었거나 선택한 학회에 속하지 않는 항목이 있습니다. 목록을 다시 조회해 주세요.");
        Set<String> suppressed = new HashSet<>();
        campaigns.findActiveSuppressions(key).forEach(email -> suppressed.add(normalize(email)));
        Map<String, List<Source>> grouped = new LinkedHashMap<>();
        int invalid = 0, duplicates = 0;
        for (Source source : sources) {
            String email = normalize(source.getEmail());
            if (!validEmail(email)) invalid++;
            else if (grouped.containsKey(email)) duplicates++;
            grouped.computeIfAbsent(email, unused -> new ArrayList<>()).add(source);
        }
        int suppressionCount = (int) grouped.keySet().stream().filter(MailHistoryService::validEmail).filter(suppressed::contains).count();
        int excluded = (int) grouped.keySet().stream().filter(email -> !validEmail(email) || suppressed.contains(email)).count();
        if (grouped.size() == excluded) throw new IllegalArgumentException("이메일 오류·수신 거부를 제외하면 저장할 유효 수신자가 없습니다.");

        MailCampaign campaign = MailCampaign.builder().subject(subject).htmlContent(html).textContent(Jsoup.parse(html).text())
                .senderName("").senderEmail("").mailType("INFORMATION").status("SAVED")
                .trackOpens(false).trackClicks(false).createdBy(adminSeq).build();
        campaigns.insert(conferenceSeq, campaign, key);
        MailSendJob job = new MailSendJob();
        histories.insertJob(job, campaign.getSeq(), "history:" + context.getSeq(), grouped.size(), excluded, adminSeq);
        context.setCampaignSeq(campaign.getSeq());
        context.setJobSeq(job.getSeq());
        context.setSelectedCount(ids.size());
        context.setDuplicateCount(duplicates);
        context.setInvalidCount(invalid);
        context.setSuppressionCount(suppressionCount);
        histories.complete(context);
        for (Map.Entry<String, List<Source>> entry : grouped.entrySet()) {
            Source first = entry.getValue().get(0);
            String email = entry.getKey();
            String reason = !validEmail(email) ? "INVALID_EMAIL" : suppressed.contains(email) ? "SUPPRESSED" : null;
            String state = reason == null ? "SAVED" : "EXCLUDED";
            MailRecipientCandidate candidate = MailRecipientCandidate.builder().email(Objects.toString(first.getEmail(), "").trim())
                    .normalizedEmail(email).fullName(first.getFullName()).affiliation(first.getAffiliation()).build();
            MailCampaignRecipient recipient = new MailCampaignRecipient();
            String token = UUID.randomUUID().toString();
            campaigns.insertRecipient(recipient, job.getSeq(), campaign.getSeq(), candidate, state, reason, token, hash(token.getBytes(StandardCharsets.UTF_8)), key);
            campaigns.insertSendResult(recipient.getSeq(), "none", state);
            for (Source source : entry.getValue()) histories.origin(context.getSeq(), recipient.getSeq(), sourceType(request.getSourceMenu()),
                    source.getSeq(), Objects.toString(source.getSourceLabel(), String.valueOf(source.getSeq())));
        }
        saveAttachments(campaign.getSeq(), files);
        return saved(context);
    }

    @Transactional(readOnly = true)
    public Page page(Filter filter, int page, int size) {
        validateFilter(filter, false);
        String key = personalData.requireDbEncString();
        Summary summary = histories.summary(filter, key);
        int safeSize = Math.max(1, Math.min(size, 100));
        int totalPages = Math.max(1, (int) Math.ceil((double) summary.getTotalCount() / safeSize));
        int safePage = Math.min(Math.max(1, page), totalPages);
        return new Page(histories.page(filter, key, safeSize, (long) (safePage - 1) * safeSize), safePage, totalPages, summary);
    }

    @Transactional(readOnly = true)
    public EmailPage emails(Filter filter, int page, int size) {
        validateFilter(filter, true);
        filter.setExactEmail(null);
        String key = personalData.requireDbEncString();
        EmailSummary summary = histories.emailSummary(filter, key);
        int safeSize = Math.max(1, Math.min(size, 100));
        int totalPages = Math.max(1, (int) Math.ceil((double) summary.getTotalCount() / safeSize));
        int safePage = Math.min(Math.max(1, page), totalPages);
        return new EmailPage(histories.emails(filter, key, safeSize, (long) (safePage - 1) * safeSize), safePage, totalPages, summary);
    }

    @Transactional(readOnly = true)
    public EmailHistoryPage emailHistories(Filter filter, Long recipientSeq, int page, int size) {
        validateFilter(filter, true);
        if (recipientSeq == null || recipientSeq <= 0) throw new IllegalArgumentException("수신자 이력이 올바르지 않습니다.");
        String key = personalData.requireDbEncString();
        String email = histories.recipientEmail(filter.getConferenceSeq(), recipientSeq, key);
        if (email == null) throw new IllegalArgumentException("선택한 학회에서 수신자 이력을 찾을 수 없습니다.");
        filter.setExactEmail(email);
        EmailSummary summary = histories.emailSummary(filter, key);
        summary.setTotalCount(summary.getHistoryCount());
        int safeSize = Math.max(1, Math.min(size, 100));
        int totalPages = Math.max(1, (int) Math.ceil((double) summary.getTotalCount() / safeSize));
        int safePage = Math.min(Math.max(1, page), totalPages);
        return new EmailHistoryPage(email, histories.emailHistories(filter, key, safeSize, (long) (safePage - 1) * safeSize), safePage, totalPages, summary);
    }

    private void validateFilter(Filter filter, boolean emailView) {
        if (filter.getConferenceSeq() == null || filter.getConferenceSeq() <= 0) throw new IllegalArgumentException("학회를 선택해 주세요.");
        filter.setKeyword(Objects.toString(filter.getKeyword(), "").trim());
        if (filter.getKeyword().length() > 200) throw new IllegalArgumentException("검색어는 200자 이하로 입력해 주세요.");
        if (!filter.getSourceMenu().isEmpty() && !MENUS.contains(filter.getSourceMenu())) throw new IllegalArgumentException("메뉴 조건이 올바르지 않습니다.");
        Set<String> statuses = emailView
                ? Set.of("", "SAVED", "EXCLUDED", "PENDING", "QUEUED", "SCHEDULED", "SENDING", "COMPLETED", "FAILED", "ACCEPTED", "DELIVERED")
                : Set.of("", "SAVED", "QUEUED", "SCHEDULED", "SENDING", "COMPLETED", "FAILED");
        if (!statuses.contains(filter.getStatus())) throw new IllegalArgumentException("상태 조건이 올바르지 않습니다.");
        if (filter.getDateFrom() != null && filter.getDateTo() != null && filter.getDateTo().isBefore(filter.getDateFrom())) throw new IllegalArgumentException("조회 종료일은 시작일보다 빠를 수 없습니다.");
        if (filter.getSourceSeq() != null && (filter.getSourceSeq() <= 0 || !Set.of("PRE_REGISTRATION", "ABSTRACT", "SPEAKER", "SPONSORSHIP").contains(filter.getSourceType()))) throw new IllegalArgumentException("원본 항목 조건이 올바르지 않습니다.");
    }

    @Transactional(readOnly = true)
    public Detail detail(Long conferenceSeq, Long seq) {
        String key = personalData.requireDbEncString();
        Item item = requireHistory(conferenceSeq, seq);
        return new Detail(item, histories.recipients(conferenceSeq, seq, key), histories.origins(conferenceSeq, seq), campaigns.findAttachments(item.getCampaignSeq()));
    }

    public Item requireHistory(Long conferenceSeq, Long seq) {
        Item item = histories.detail(conferenceSeq, seq, personalData.requireDbEncString());
        if (item == null) throw new IllegalArgumentException("메일 이력을 찾을 수 없습니다.");
        return item;
    }

    private void saveAttachments(Long campaignSeq, List<MultipartFile> files) {
        List<Path> written = new ArrayList<>();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) cleanup(written);
                }
            });
        }
        try {
            for (MultipartFile file : files) {
                String filename = file.getOriginalFilename();
                UploadStorage.StoredTarget target = storage.monthlyTarget(UploadStorage.MAIL, UUID.randomUUID() + "." + extension(filename));
                written.add(storage.resolve(UploadStorage.MAIL, target.relativePath()));
                Files.createDirectories(target.path().getParent());
                file.transferTo(target.path());
                campaigns.insertAttachment(MailAttachment.builder().campaignSeq(campaignSeq).originalFilename(filename)
                        .savedFilename(target.relativePath()).contentType(file.getContentType()).fileSize(file.getSize()).build());
            }
        } catch (IOException | RuntimeException e) {
            cleanup(written);
            throw new IllegalStateException("첨부파일 저장에 실패했습니다. 다시 시도해 주세요.", e);
        }
    }

    private static void cleanup(List<Path> paths) {
        for (Path path : paths) try { Files.deleteIfExists(path); } catch (IOException ignored) { /* Preserve original failure. */ }
    }

    private static void validateFiles(List<MultipartFile> files) {
        if (files.size() > 5) throw new IllegalArgumentException("첨부파일은 최대 5개까지 선택할 수 있습니다.");
        long total = 0;
        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename();
            if (file.isEmpty() || filename == null || filename.isBlank() || filename.length() > 255
                    || filename.contains("/") || filename.contains("\\") || filename.chars().anyMatch(Character::isISOControl)
                    || !EXTENSIONS.contains(extension(filename))) throw new IllegalArgumentException("첨부파일의 이름·형식·크기를 확인해 주세요.");
            total += file.getSize();
            if (total > 10 * 1024 * 1024) throw new IllegalArgumentException("첨부파일의 전체 용량은 10MB 이하여야 합니다.");
        }
    }

    private String fingerprint(String menu, String subject, String html, List<Long> ids, List<MultipartFile> files) {
        try {
            List<List<Object>> attachments = new ArrayList<>();
            for (MultipartFile file : files) attachments.add(List.of(file.getOriginalFilename(), file.getSize(), hash(file.getBytes())));
            return hash(json.writeValueAsBytes(List.of(menu, subject, html, ids, attachments)));
        } catch (IOException e) { throw new IllegalStateException("저장 요청을 확인하지 못했습니다.", e); }
    }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String extension(String filename) { return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT); }
    private static String normalize(String email) { return Objects.toString(email, "").trim().toLowerCase(Locale.ROOT); }
    private static boolean validEmail(String email) { return email.length() <= 255 && EMAIL.matcher(email).matches(); }
    private static String sourceType(String menu) {
        return switch (menu) { case "pre-registrations" -> "PRE_REGISTRATION"; case "speakers" -> "SPEAKER"; case "sponsorship" -> "SPONSORSHIP"; default -> "ABSTRACT"; };
    }
    private static Saved saved(Context c) { return new Saved(c.getSeq(), c.getSelectedCount(), c.getDuplicateCount(), c.getInvalidCount(), c.getSuppressionCount()); }
}
