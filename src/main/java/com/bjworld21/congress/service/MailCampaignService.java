package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PromotionalMailProperties;
import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.MailCampaignDetailResponse;
import com.bjworld21.congress.dto.MailCampaignPageResponse;
import com.bjworld21.congress.dto.MailCampaignRequest;
import com.bjworld21.congress.dto.MailDirectRecipientRequest;
import com.bjworld21.congress.dto.MailQueueRequest;
import com.bjworld21.congress.dto.MailQueueResponse;
import com.bjworld21.congress.dto.MailRecipientSelectionRequest;
import com.bjworld21.congress.entity.MailAttachment;
import com.bjworld21.congress.entity.MailCampaign;
import com.bjworld21.congress.entity.MailCampaignRecipient;
import com.bjworld21.congress.entity.MailRecipientCandidate;
import com.bjworld21.congress.entity.MailSendJob;
import com.bjworld21.congress.repository.MailCampaignRepository;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class MailCampaignService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip",
            "png", "jpg", "jpeg", "gif", "webp", "txt"
    );
    private final MailCampaignRepository repository;
    private final MailAddressBookService addressBookService;
    private final MailHtmlSanitizer htmlSanitizer;
    private final PromotionalMailProperties properties;
    private final UploadStorage uploadStorage;
    private final MailRecipientSelectionService recipientSelectionService;
    private final PersonalDataProperties personalDataProperties;

    public MailCampaignService(
            MailCampaignRepository repository,
            MailAddressBookService addressBookService,
            MailHtmlSanitizer htmlSanitizer,
            PromotionalMailProperties properties,
            UploadStorage uploadStorage,
            MailRecipientSelectionService recipientSelectionService,
            PersonalDataProperties personalDataProperties
    ) {
        this.repository = repository;
        this.addressBookService = addressBookService;
        this.htmlSanitizer = htmlSanitizer;
        this.properties = properties;
        this.uploadStorage = uploadStorage;
        this.recipientSelectionService = recipientSelectionService;
        this.personalDataProperties = personalDataProperties;
    }

    public MailCampaignPageResponse findPage(Long conferenceSeq, int page, int size, String keyword) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        long totalCount = repository.count(conferenceSeq, normalizedKeyword);
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / safeSize));
        int adjustedPage = Math.min(safePage, totalPages);
        return MailCampaignPageResponse.builder()
                .items(repository.findPage(conferenceSeq, normalizedKeyword, safeSize, (adjustedPage - 1) * safeSize, dbEncString()))
                .page(adjustedPage)
                .size(safeSize)
                .totalCount(totalCount)
                .totalPages(totalPages)
                .build();
    }

    public MailCampaignDetailResponse get(Long conferenceSeq, Long seq) {
        MailCampaign campaign = requireCampaign(conferenceSeq, seq);
        return MailCampaignDetailResponse.builder()
                .campaign(campaign)
                .recipientGroups(repository.findGroupSources(seq))
                .addressBookSeqs(repository.findAddressBookSources(seq))
                .directRecipients(repository.findDirectSources(seq, dbEncString()))
                .attachments(repository.findAttachments(seq))
                .build();
    }

    @Transactional
    public MailCampaignDetailResponse create(Long conferenceSeq, MailCampaignRequest request) {
        MailCampaign campaign = toCampaign(request);
        campaign.setStatus("DRAFT");
        repository.insert(conferenceSeq, campaign, dbEncString());
        saveSources(campaign.getSeq(), request);
        return get(conferenceSeq, campaign.getSeq());
    }

    @Transactional
    public MailCampaignDetailResponse update(Long conferenceSeq, Long seq, MailCampaignRequest request) {
        MailCampaign existing = requireDraft(conferenceSeq, seq);
        MailCampaign campaign = toCampaign(request);
        campaign.setSeq(seq);
        campaign.setVersionNo(request.getVersionNo() == null ? existing.getVersionNo() : request.getVersionNo());
        if (repository.update(conferenceSeq, campaign, dbEncString()) == 0) {
            throw new IllegalStateException("다른 관리자가 수정했거나 더 이상 수정할 수 없는 캠페인입니다. 새로고침 후 다시 시도해 주세요.");
        }
        repository.deleteSources(seq);
        saveSources(seq, request);
        return get(conferenceSeq, seq);
    }

    public void delete(Long conferenceSeq, Long seq) {
        MailCampaign campaign = requireDraft(conferenceSeq, seq);
        List<MailAttachment> attachments = repository.findAttachments(seq);
        if (repository.delete(conferenceSeq, campaign.getSeq()) == 0) {
            throw new IllegalStateException("작성 중인 캠페인만 삭제할 수 있습니다.");
        }
        attachments.forEach(attachment -> deleteStoredFile(attachment.getSavedFilename()));
    }

    public MailAttachment addAttachment(Long conferenceSeq, Long campaignSeq, MultipartFile file) {
        requireDraft(conferenceSeq, campaignSeq);
        validateAttachment(file, campaignSeq);
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String extension = extension(originalFilename);
        UploadStorage.StoredTarget storedTarget = uploadStorage.monthlyTarget(
                UploadStorage.MAIL,
                UUID.randomUUID() + "." + extension
        );
        String savedFilename = storedTarget.relativePath();
        try {
            Path target = storedTarget.path();
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            MailAttachment attachment = MailAttachment.builder()
                    .campaignSeq(campaignSeq)
                    .originalFilename(originalFilename)
                    .savedFilename(savedFilename)
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .build();
            repository.insertAttachment(attachment);
            return repository.findAttachment(attachment.getSeq());
        } catch (IOException exception) {
            deleteStoredFile(savedFilename);
            throw new IllegalStateException("첨부파일 저장에 실패했습니다.", exception);
        } catch (RuntimeException exception) {
            deleteStoredFile(savedFilename);
            throw exception;
        }
    }

    public void deleteAttachment(Long conferenceSeq, Long campaignSeq, Long attachmentSeq) {
        requireDraft(conferenceSeq, campaignSeq);
        MailAttachment attachment = repository.findAttachment(attachmentSeq);
        if (attachment == null || !campaignSeq.equals(attachment.getCampaignSeq())) {
            throw new IllegalArgumentException("존재하지 않는 첨부파일입니다.");
        }
        repository.deleteAttachment(attachmentSeq);
        deleteStoredFile(attachment.getSavedFilename());
    }

    public Resource getAttachment(Long conferenceSeq, Long campaignSeq, Long attachmentSeq) {
        requireCampaign(conferenceSeq, campaignSeq);
        MailAttachment attachment = repository.findAttachment(attachmentSeq);
        if (attachment == null || !campaignSeq.equals(attachment.getCampaignSeq())) {
            throw new IllegalArgumentException("존재하지 않는 첨부파일입니다.");
        }
        try {
            Path target = uploadStorage.resolve(UploadStorage.MAIL, attachment.getSavedFilename());
            if (!Files.isRegularFile(target)) {
                throw new IllegalArgumentException("첨부파일을 찾을 수 없습니다.");
            }
            return new UrlResource(target.toUri());
        } catch (MalformedURLException exception) {
            throw new IllegalStateException("첨부파일을 읽을 수 없습니다.", exception);
        }
    }

    public MailAttachment requireAttachment(Long conferenceSeq, Long campaignSeq, Long attachmentSeq) {
        requireCampaign(conferenceSeq, campaignSeq);
        MailAttachment attachment = repository.findAttachment(attachmentSeq);
        if (attachment == null || !campaignSeq.equals(attachment.getCampaignSeq())) {
            throw new IllegalArgumentException("존재하지 않는 첨부파일입니다.");
        }
        return attachment;
    }

    @Transactional
    public MailQueueResponse prepareJob(Long conferenceSeq, Long campaignSeq, MailQueueRequest request) {
        MailCampaign campaign = requireDraft(conferenceSeq, campaignSeq);
        if ("ADVERTISEMENT".equals(campaign.getMailType())
                && !campaign.getHtmlContent().contains("{{unsubscribeUrl}}")) {
            throw new IllegalArgumentException("광고성 메일에는 {{unsubscribeUrl}} 수신 거부 링크가 필요합니다.");
        }
        if (repository.findJobByIdempotencyKey(request.getIdempotencyKey()) != null) {
            throw new IllegalArgumentException("이미 처리된 발송 준비 요청입니다.");
        }

        MailRecipientSelectionRequest selectionRequest = new MailRecipientSelectionRequest();
        selectionRequest.setRecipientGroups(repository.findGroupSources(campaignSeq));
        selectionRequest.setAddressBookSeqs(repository.findAddressBookSources(campaignSeq));
        selectionRequest.setDirectRecipients(repository.findDirectSources(campaignSeq, dbEncString()));
        MailRecipientSelectionService.Selection selection = recipientSelectionService.resolve(conferenceSeq, selectionRequest);
        if (selection.includedCount() == 0) {
            throw new IllegalArgumentException("중복·수신 거부·이메일 오류를 확인한 뒤 발송 가능한 수신자가 없습니다.");
        }
        List<PreparedRecipient> preparedRecipients = new ArrayList<>();
        for (MailRecipientCandidate candidate : selection.recipients()) {
            String exclusionReason = selection.suppressions().contains(candidate.getNormalizedEmail()) ? "SUPPRESSED" : null;
            preparedRecipients.add(new PreparedRecipient(candidate, exclusionReason));
        }
        int excludedCount = selection.suppressionCount();
        MailSendJob job = new MailSendJob();
        repository.insertJob(job, campaignSeq, request.getIdempotencyKey(),
                properties.getProvider(), preparedRecipients.size(), excludedCount, campaign.getScheduledAt());

        for (PreparedRecipient prepared : preparedRecipients) {
            String token = randomToken();
            MailCampaignRecipient recipient = new MailCampaignRecipient();
            String status = prepared.exclusionReason() == null ? "PENDING" : "EXCLUDED";
            repository.insertRecipient(recipient, job.getSeq(), campaignSeq, prepared.candidate(), status,
                    prepared.exclusionReason(), token, sha256(token), dbEncString());
            repository.insertSendResult(recipient.getSeq(), properties.getProvider(), status);
        }

        String nextStatus = campaign.getScheduledAt() != null && campaign.getScheduledAt().isAfter(LocalDateTime.now())
                ? "SCHEDULED" : "QUEUED";
        if (repository.updateStatus(conferenceSeq, campaignSeq, nextStatus) == 0) {
            throw new IllegalStateException("캠페인 상태를 발송 준비 상태로 변경하지 못했습니다.");
        }

        return MailQueueResponse.builder()
                .jobSeq(job.getSeq())
                .status("QUEUED")
                .totalCount(preparedRecipients.size())
                .includedCount(preparedRecipients.size() - excludedCount)
                .excludedCount(excludedCount)
                .duplicateCount(selection.duplicateCount())
                .suppressionCount(selection.suppressionCount())
                .invalidCount(selection.invalidCount())
                .build();
    }

    private void saveSources(Long campaignSeq, MailCampaignRequest request) {
        for (String group : recipientSelectionService.validateGroups(request.getRecipientGroups())) {
            repository.insertGroupSource(campaignSeq, group);
        }
        Set<Long> uniqueBookSeqs = new LinkedHashSet<>(request.getAddressBookSeqs() == null
                ? List.of() : request.getAddressBookSeqs());
        for (Long addressBookSeq : uniqueBookSeqs) {
            addressBookService.requireAddressBook(addressBookSeq);
            repository.insertAddressBookSource(campaignSeq, addressBookSeq);
        }

        Set<String> directEmails = new LinkedHashSet<>();
        for (MailDirectRecipientRequest recipient : request.getDirectRecipients() == null
                ? List.<MailDirectRecipientRequest>of() : request.getDirectRecipients()) {
            String normalizedEmail = MailAddressBookService.normalizeEmail(recipient.getEmail());
            if (directEmails.add(normalizedEmail)) {
                if (recipient.getSourceType() == null || recipient.getSourceType().isBlank()) {
                    recipient.setSourceType("DIRECT");
                }
                if (!Set.of("DIRECT", "INTERNAL_MEMBER", "INTERNAL_ADMIN", "ADDRESS_BOOK_CONTACT").contains(recipient.getSourceType())) {
                    throw new IllegalArgumentException("개별 수신자 구분이 올바르지 않습니다.");
                }
                recipient.setEmail(recipient.getEmail().trim());
                recipient.setFullName(trimToNull(recipient.getFullName()));
                recipient.setAffiliation(trimToNull(recipient.getAffiliation()));
                repository.insertDirectSource(campaignSeq, recipient, normalizedEmail, dbEncString());
            }
        }
    }

    private MailCampaign toCampaign(MailCampaignRequest request) {
        String sanitizedHtml = htmlSanitizer.sanitize(request.getHtmlContent());
        if (!htmlSanitizer.hasVisibleContent(sanitizedHtml)) {
            throw new IllegalArgumentException("메일 본문에 표시할 내용을 입력해 주세요.");
        }
        return MailCampaign.builder()
                .subject(request.getSubject().trim())
                .senderName(request.getSenderName().trim())
                .senderEmail(request.getSenderEmail().trim())
                .replyToEmail(trimToNull(request.getReplyToEmail()))
                .htmlContent(sanitizedHtml)
                .textContent(trimToNull(request.getTextContent()))
                .mailType(request.getMailType() == null ? "ADVERTISEMENT" : request.getMailType())
                .trackOpens(Boolean.TRUE.equals(request.getTrackOpens()))
                .trackClicks(Boolean.TRUE.equals(request.getTrackClicks()))
                .scheduledAt(request.getScheduledAt())
                .build();
    }

    private MailCampaign requireCampaign(Long conferenceSeq, Long seq) {
        MailCampaign campaign = repository.findBySeq(conferenceSeq, seq, dbEncString());
        if (campaign == null) {
            throw new IllegalArgumentException("존재하지 않는 메일 캠페인입니다.");
        }
        return campaign;
    }

    private MailCampaign requireDraft(Long conferenceSeq, Long seq) {
        MailCampaign campaign = requireCampaign(conferenceSeq, seq);
        if (!"DRAFT".equals(campaign.getStatus())) {
            throw new IllegalStateException("작성 중인 캠페인만 변경할 수 있습니다.");
        }
        return campaign;
    }

    private void validateAttachment(MultipartFile file, Long campaignSeq) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("첨부파일을 선택해 주세요.");
        }
        if (repository.countAttachments(campaignSeq) >= properties.getMaxAttachmentCount()) {
            throw new IllegalArgumentException("첨부파일은 최대 " + properties.getMaxAttachmentCount() + "개까지 등록할 수 있습니다.");
        }
        if (repository.sumAttachmentBytes(campaignSeq) + file.getSize() > properties.getMaxAttachmentTotalBytes()) {
            throw new IllegalArgumentException("첨부파일 전체 크기는 10MB를 초과할 수 없습니다.");
        }
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (originalFilename.contains("..") || !ALLOWED_EXTENSIONS.contains(extension(originalFilename))) {
            throw new IllegalArgumentException("허용되지 않는 첨부파일 형식입니다.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (contentType.contains("executable") || contentType.contains("x-msdownload")
                || contentType.contains("javascript") || contentType.contains("html")) {
            throw new IllegalArgumentException("위험한 형식의 첨부파일은 등록할 수 없습니다.");
        }
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void deleteStoredFile(String savedFilename) {
        if (savedFilename == null || savedFilename.isBlank()) {
            return;
        }
        try {
            Path target = uploadStorage.resolve(UploadStorage.MAIL, savedFilename);
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // DB 상태 변경을 오래된 파일 정리 실패로 되돌리지 않는다.
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record PreparedRecipient(MailRecipientCandidate candidate, String exclusionReason) {
    }
    private String dbEncString() {
        return personalDataProperties.requireDbEncString();
    }
}
