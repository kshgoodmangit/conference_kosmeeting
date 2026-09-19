package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.config.AdminRolePolicy;
import com.bjworld21.conference.dto.MaintenanceRequestAttachmentResponse;
import com.bjworld21.conference.dto.MaintenanceRequestPageResponse;
import com.bjworld21.conference.dto.MaintenanceRequestResponse;
import com.bjworld21.conference.entity.AdminAccount;
import com.bjworld21.conference.entity.MaintenanceRequest;
import com.bjworld21.conference.entity.MaintenanceRequestAttachment;
import com.bjworld21.conference.entity.MaintenanceRequestNotification;
import com.bjworld21.conference.repository.AdminAccountRepository;
import com.bjworld21.conference.repository.MaintenanceRequestRepository;
import org.jsoup.Jsoup;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class MaintenanceRequestService {
    private static final int MAX_FILE_COUNT = 5;
    private static final long MAX_TOTAL_FILE_BYTES = 20L * 1024 * 1024;
    private static final Set<String> RESPONSE_STATUSES = Set.of("REQUESTED", "IN_PROGRESS", "COMPLETED");

    private final MaintenanceRequestRepository repository;
    private final AdminAccountRepository adminAccountRepository;
    private final PersonalDataProperties personalDataProperties;
    private final CmsHtmlSanitizer htmlSanitizer;
    private final UploadStorage uploadStorage;
    private final ApplicationEventPublisher eventPublisher;

    public MaintenanceRequestService(
            MaintenanceRequestRepository repository,
            AdminAccountRepository adminAccountRepository,
            PersonalDataProperties personalDataProperties,
            CmsHtmlSanitizer htmlSanitizer,
            UploadStorage uploadStorage,
            ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.adminAccountRepository = adminAccountRepository;
        this.personalDataProperties = personalDataProperties;
        this.htmlSanitizer = htmlSanitizer;
        this.uploadStorage = uploadStorage;
        this.eventPublisher = eventPublisher;
    }

    public MaintenanceRequestPageResponse findPage(Long conferenceSeq, int page, int size, String keyword, String status) {
        requireConference(conferenceSeq);
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedKeyword = trimToNull(keyword);
        String normalizedStatus = normalizeSearchStatus(status);
        long totalCount = repository.countPage(conferenceSeq, normalizedKeyword, normalizedStatus);
        List<MaintenanceRequestResponse> items = repository.findPage(
                        conferenceSeq, normalizedKeyword, normalizedStatus,
                        safeSize, (safePage - 1) * safeSize, dbEncString())
                .stream().map(this::toResponse).toList();

        return MaintenanceRequestPageResponse.builder()
                .items(items)
                .page(safePage)
                .size(safeSize)
                .totalCount(totalCount)
                .requestedCount(repository.countByStatus(conferenceSeq, "REQUESTED", normalizedKeyword))
                .inProgressCount(repository.countByStatus(conferenceSeq, "IN_PROGRESS", normalizedKeyword))
                .completedCount(repository.countByStatus(conferenceSeq, "COMPLETED", normalizedKeyword))
                .build();
    }

    public MaintenanceRequestResponse get(Long conferenceSeq, Long seq) {
        MaintenanceRequest request = requireRequest(conferenceSeq, seq);
        MaintenanceRequestResponse response = toResponse(request);
        response.setAttachments(repository.findAttachments(seq).stream().map(this::toAttachmentResponse).toList());
        return response;
    }

    @Transactional
    public MaintenanceRequestResponse create(
            Long conferenceSeq,
            Long adminSeq,
            String title,
            String content,
            List<MultipartFile> files
    ) {
        requireConference(conferenceSeq);
        AdminAccount requester = requireFullAdministrator(adminSeq);
        String normalizedTitle = requireText(title, "제목", 255);
        String sanitizedContent = requireHtml(content, "요청 내용");
        List<MultipartFile> uploadFiles = validateFiles(files);
        List<Path> storedPaths = new ArrayList<>();

        try {
            MaintenanceRequest request = new MaintenanceRequest();
            request.setConferenceSeq(conferenceSeq);
            request.setTitle(normalizedTitle);
            request.setContent(sanitizedContent);
            request.setRequestedByAdminSeq(adminSeq);
            repository.insert(request);

            saveAttachments(request.getSeq(), adminSeq, "REQUEST", uploadFiles, storedPaths);

            List<MaintenanceRequestCreatedEvent.Recipient> recipients = new ArrayList<>();
            for (AdminAccount recipient : adminAccountRepository.findAllActiveByRole("maintenance", dbEncString())) {
                if (recipient.getContactEmail() == null || recipient.getContactEmail().isBlank()) continue;
                MaintenanceRequestNotification notification = new MaintenanceRequestNotification();
                notification.setMaintenanceRequestSeq(request.getSeq());
                notification.setRecipientAdminSeq(recipient.getSeq());
                repository.insertNotification(notification);
                recipients.add(new MaintenanceRequestCreatedEvent.Recipient(
                        notification.getSeq(), recipient.getContactEmail(), recipient.getAdminName()));
            }
            eventPublisher.publishEvent(new MaintenanceRequestCreatedEvent(
                    request.getSeq(), normalizedTitle, sanitizedContent, requester.getAdminName(), recipients));
            return get(conferenceSeq, request.getSeq());
        } catch (RuntimeException | IOException exception) {
            storedPaths.forEach(this::deleteQuietly);
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalStateException("첨부파일을 저장하지 못했습니다.", exception);
        }
    }

    @Transactional
    public MaintenanceRequestResponse update(
            Long conferenceSeq,
            Long adminSeq,
            Long seq,
            String title,
            String content,
            List<MultipartFile> files
    ) {
        requireFullAdministrator(adminSeq);
        requireRequest(conferenceSeq, seq);
        String normalizedTitle = requireText(title, "제목", 255);
        String sanitizedContent = requireHtml(content, "요청 내용");
        List<MultipartFile> uploadFiles = validateFiles(files);
        List<MaintenanceRequestAttachment> requestAttachments = repository.findAttachments(seq).stream()
                .filter(item -> "REQUEST".equals(item.getAttachmentType()))
                .toList();
        if (requestAttachments.size() + uploadFiles.size() > MAX_FILE_COUNT) {
            throw new IllegalArgumentException("요청 첨부파일은 누적 최대 5개까지 등록할 수 있습니다.");
        }
        long totalBytes = requestAttachments.stream()
                .mapToLong(item -> item.getFileSize() == null ? 0L : item.getFileSize())
                .sum()
                + uploadFiles.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalBytes > MAX_TOTAL_FILE_BYTES) {
            throw new IllegalArgumentException("요청 첨부파일 전체 용량은 누적 20MB 이하여야 합니다.");
        }

        List<Path> storedPaths = new ArrayList<>();
        try {
            if (repository.updateRequest(conferenceSeq, seq, normalizedTitle, sanitizedContent) == 0) {
                throw new IllegalArgumentException("유지보수 요청을 찾을 수 없습니다.");
            }
            saveAttachments(seq, adminSeq, "REQUEST", uploadFiles, storedPaths, requestAttachments.size());
            return get(conferenceSeq, seq);
        } catch (RuntimeException | IOException exception) {
            storedPaths.forEach(this::deleteQuietly);
            if (exception instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalStateException("유지보수 요청을 수정하지 못했습니다.", exception);
        }
    }

    @Transactional
    public MaintenanceRequestResponse saveAnswer(
            Long conferenceSeq,
            Long adminSeq,
            Long seq,
            String status,
            String answerContent,
            List<MultipartFile> files
    ) {
        requireFullAdministrator(adminSeq);
        requireRequest(conferenceSeq, seq);
        String normalizedStatus = status == null ? "IN_PROGRESS" : status.trim().toUpperCase(Locale.ROOT);
        if (!RESPONSE_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("올바른 처리 상태를 선택해 주세요.");
        }
        String sanitizedAnswer = answerContent == null || answerContent.isBlank()
                ? "" : htmlSanitizer.sanitize(answerContent);
        List<MultipartFile> uploadFiles = validateFiles(files);
        List<Path> storedPaths = new ArrayList<>();
        try {
            if (repository.updateAnswer(conferenceSeq, seq, normalizedStatus, sanitizedAnswer, adminSeq) == 0) {
                throw new IllegalArgumentException("유지보수 요청을 찾을 수 없습니다.");
            }
            int currentAnswerFileCount = (int) repository.findAttachments(seq).stream()
                    .filter(item -> "ANSWER".equals(item.getAttachmentType())).count();
            if (currentAnswerFileCount + uploadFiles.size() > MAX_FILE_COUNT) {
                throw new IllegalArgumentException("답변 첨부파일은 누적 최대 5개까지 등록할 수 있습니다.");
            }
            saveAttachments(seq, adminSeq, "ANSWER", uploadFiles, storedPaths, currentAnswerFileCount);
            return get(conferenceSeq, seq);
        } catch (RuntimeException | IOException exception) {
            storedPaths.forEach(this::deleteQuietly);
            if (exception instanceof IllegalArgumentException illegalArgumentException) throw illegalArgumentException;
            throw new IllegalStateException("처리 결과를 저장하지 못했습니다.", exception);
        }
    }

    @Transactional
    public void delete(Long conferenceSeq, Long adminSeq, Long seq) {
        requireFullAdministrator(adminSeq);
        requireRequest(conferenceSeq, seq);
        if (repository.softDelete(conferenceSeq, seq) == 0) {
            throw new IllegalArgumentException("유지보수 요청을 찾을 수 없습니다.");
        }
    }

    public DownloadFile getAttachment(Long conferenceSeq, Long attachmentSeq) {
        MaintenanceRequestAttachment attachment = repository.findAttachment(conferenceSeq, attachmentSeq);
        if (attachment == null) throw new IllegalArgumentException("첨부파일을 찾을 수 없습니다.");
        Path path = uploadStorage.resolve(UploadStorage.MAINTENANCE, attachment.getSavedFilename());
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("첨부파일이 존재하지 않습니다.");
        return new DownloadFile(path, attachment.getOriginalFilename(), attachment.getContentType());
    }

    private void saveAttachments(Long requestSeq, Long adminSeq, String type, List<MultipartFile> files,
                                 List<Path> storedPaths) throws IOException {
        saveAttachments(requestSeq, adminSeq, type, files, storedPaths, 0);
    }

    private void saveAttachments(Long requestSeq, Long adminSeq, String type, List<MultipartFile> files,
                                 List<Path> storedPaths, int sortOffset) throws IOException {
        int sortOrder = sortOffset;
        for (MultipartFile file : files) {
            String originalFilename = safeOriginalFilename(file.getOriginalFilename());
            String extension = extensionOf(originalFilename);
            String savedFilename = UUID.randomUUID() + (extension.isBlank() ? "" : "." + extension);
            UploadStorage.StoredTarget target = uploadStorage.monthlyTarget(UploadStorage.MAINTENANCE, savedFilename);
            Files.createDirectories(target.path().getParent());
            file.transferTo(target.path());
            storedPaths.add(target.path());

            MaintenanceRequestAttachment attachment = new MaintenanceRequestAttachment();
            attachment.setMaintenanceRequestSeq(requestSeq);
            attachment.setAttachmentType(type);
            attachment.setOriginalFilename(originalFilename);
            attachment.setSavedFilename(target.relativePath());
            attachment.setContentType(trimToNull(file.getContentType()));
            attachment.setFileExtension(extension);
            attachment.setFileSize(file.getSize());
            attachment.setSortOrder(sortOrder++);
            attachment.setUploadedByAdminSeq(adminSeq);
            repository.insertAttachment(attachment);
        }
    }

    private List<MultipartFile> validateFiles(List<MultipartFile> files) {
        List<MultipartFile> result = files == null ? List.of() : files.stream()
                .filter(file -> file != null && !file.isEmpty()).toList();
        if (result.size() > MAX_FILE_COUNT) throw new IllegalArgumentException("첨부파일은 최대 5개까지 등록할 수 있습니다.");
        long totalBytes = result.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalBytes > MAX_TOTAL_FILE_BYTES) throw new IllegalArgumentException("첨부파일 전체 용량은 20MB 이하여야 합니다.");
        return result;
    }

    private MaintenanceRequest requireRequest(Long conferenceSeq, Long seq) {
        requireConference(conferenceSeq);
        if (seq == null) throw new IllegalArgumentException("요청 번호가 필요합니다.");
        MaintenanceRequest request = repository.findBySeq(conferenceSeq, seq, dbEncString());
        if (request == null) throw new IllegalArgumentException("유지보수 요청을 찾을 수 없습니다.");
        return request;
    }

    private AdminAccount requireFullAdministrator(Long adminSeq) {
        AdminAccount admin = adminAccountRepository.findBySeq(adminSeq, dbEncString());
        if (admin == null || !"active".equals(admin.getStatus())
                || !AdminRolePolicy.isFullAdministrator(admin.getRole())) {
            throw new IllegalArgumentException("해당 작업을 수행할 권한이 없습니다.");
        }
        return admin;
    }

    private MaintenanceRequestResponse toResponse(MaintenanceRequest request) {
        return MaintenanceRequestResponse.builder()
                .seq(request.getSeq()).title(request.getTitle()).content(request.getContent())
                .status(request.getStatus()).requestedByAdminSeq(request.getRequestedByAdminSeq())
                .requestedByName(request.getRequestedByName()).assignedToAdminSeq(request.getAssignedToAdminSeq())
                .assignedToName(request.getAssignedToName()).answerContent(request.getAnswerContent())
                .answeredByAdminSeq(request.getAnsweredByAdminSeq()).answeredByName(request.getAnsweredByName())
                .answeredAt(request.getAnsweredAt()).createdAt(request.getCreatedAt()).updatedAt(request.getUpdatedAt())
                .attachmentCount(request.getAttachmentCount()).build();
    }

    private MaintenanceRequestAttachmentResponse toAttachmentResponse(MaintenanceRequestAttachment attachment) {
        return MaintenanceRequestAttachmentResponse.builder()
                .seq(attachment.getSeq()).attachmentType(attachment.getAttachmentType())
                .originalFilename(attachment.getOriginalFilename()).contentType(attachment.getContentType())
                .fileSize(attachment.getFileSize()).build();
    }

    private String requireText(String value, String label, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + "은(는) 필수입니다.");
        if (normalized.length() > maxLength) throw new IllegalArgumentException(label + "은(는) " + maxLength + "자 이하여야 합니다.");
        return normalized;
    }

    private String requireHtml(String value, String label) {
        String sanitized = htmlSanitizer.sanitize(value);
        if (Jsoup.parse(sanitized).text().isBlank() && !sanitized.contains("<img")) {
            throw new IllegalArgumentException(label + "은(는) 필수입니다.");
        }
        return sanitized;
    }

    private String normalizeSearchStatus(String status) {
        String value = trimToNull(status);
        if (value == null) return null;
        String normalized = value.toUpperCase(Locale.ROOT);
        if (!RESPONSE_STATUSES.contains(normalized)) throw new IllegalArgumentException("올바른 처리 상태가 아닙니다.");
        return normalized;
    }

    private String safeOriginalFilename(String value) {
        String name = value == null ? "attachment" : Path.of(value.replace('\\', '/')).getFileName().toString();
        if (name.isBlank()) return "attachment";
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 1 || dot == filename.length() - 1) return "";
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return extension.length() <= 20 ? extension : extension.substring(0, 20);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }

    private void requireConference(Long conferenceSeq) {
        if (conferenceSeq == null || conferenceSeq < 1) throw new IllegalArgumentException("학회 정보가 필요합니다.");
    }

    private String dbEncString() { return personalDataProperties.requireDbEncString(); }

    private void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }

    public record DownloadFile(Path path, String originalFilename, String contentType) {}
}
