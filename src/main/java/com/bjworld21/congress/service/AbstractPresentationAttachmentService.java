package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.AbstractSubmissionAttachmentResponse;
import com.bjworld21.congress.dto.AbstractSubmissionResponse;
import com.bjworld21.congress.entity.ConferenceSettings;
import com.bjworld21.congress.entity.AbstractSubmissionAttachment;
import com.bjworld21.congress.repository.AbstractSubmissionRepository;
import com.bjworld21.congress.repository.ConferenceSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AbstractPresentationAttachmentService {
    public static final long MAX_FILE_SIZE = 100L * 1024L * 1024L;
    public static final int MAX_FILE_COUNT = 5;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "ppt", "pptx");
    private static final Map<String, Set<String>> ALLOWED_CONTENT_TYPES = Map.of(
            "pdf", Set.of("application/pdf", "application/octet-stream"),
            "ppt", Set.of(
                    "application/vnd.ms-powerpoint",
                    "application/mspowerpoint",
                    "application/powerpoint",
                    "application/x-mspowerpoint",
                    "application/octet-stream"
            ),
            "pptx", Set.of(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/octet-stream"
            )
    );

    private final AbstractSubmissionRepository abstractSubmissionRepository;
    private final ConferenceSettingsRepository conferenceSettingsRepository;
    private final UploadStorage uploadStorage;
    private final PersonalDataProperties personalDataProperties;
    private final Clock clock;

    public AbstractPresentationAttachmentService(
            AbstractSubmissionRepository abstractSubmissionRepository,
            ConferenceSettingsRepository conferenceSettingsRepository,
            UploadStorage uploadStorage,
            PersonalDataProperties personalDataProperties,
            Clock clock
    ) {
        this.abstractSubmissionRepository = abstractSubmissionRepository;
        this.conferenceSettingsRepository = conferenceSettingsRepository;
        this.uploadStorage = uploadStorage;
        this.personalDataProperties = personalDataProperties;
        this.clock = clock;
    }

    public AbstractSubmissionAttachmentResponse add(Long conferenceSeq, Long abstractSeq, MultipartFile file) {
        requireApprovedAbstract(conferenceSeq, abstractSeq);
        return store(abstractSeq, file);
    }

    public AbstractSubmissionAttachmentResponse addByMember(
            Long conferenceSeq,
            Long abstractSeq,
            Long memberSeq,
            MultipartFile file
    ) {
        requireMemberUploadAccess(conferenceSeq, abstractSeq, memberSeq);
        return store(abstractSeq, file);
    }

    private AbstractSubmissionAttachmentResponse store(Long abstractSeq, MultipartFile file) {
        validateFile(file);
        if (abstractSubmissionRepository.countAttachments(abstractSeq) >= MAX_FILE_COUNT) {
            throw new IllegalArgumentException("발표 자료는 최대 5개까지 등록할 수 있습니다.");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = extension(originalFilename);
        UploadStorage.StoredTarget storedTarget = uploadStorage.monthlyTarget(
                UploadStorage.ABSTRACTS,
                UUID.randomUUID() + "." + extension
        );

        try {
            Files.createDirectories(storedTarget.path().getParent());
            file.transferTo(storedTarget.path());

            AbstractSubmissionAttachment attachment = AbstractSubmissionAttachment.builder()
                    .abstractSeq(abstractSeq)
                    .originalFilename(originalFilename)
                    .saveFilename(storedTarget.relativePath())
                    .contentType(normalizeContentType(file.getContentType()))
                    .fileExtension(extension)
                    .fileSize(file.getSize())
                    .build();
            abstractSubmissionRepository.insertAttachment(attachment);
            return toResponse(attachment);
        } catch (IOException exception) {
            deleteStoredFile(storedTarget.relativePath());
            throw new IllegalStateException("발표 자료 저장에 실패했습니다.", exception);
        } catch (RuntimeException exception) {
            deleteStoredFile(storedTarget.relativePath());
            throw exception;
        }
    }

    public void delete(Long conferenceSeq, Long abstractSeq, Long attachmentSeq) {
        requireApprovedAbstract(conferenceSeq, abstractSeq);
        delete(abstractSeq, attachmentSeq);
    }

    public void deleteByMember(Long conferenceSeq, Long abstractSeq, Long attachmentSeq, Long memberSeq) {
        requireMemberAbstractAccess(conferenceSeq, abstractSeq, memberSeq);
        delete(abstractSeq, attachmentSeq);
    }

    private void delete(Long abstractSeq, Long attachmentSeq) {
        AbstractSubmissionAttachment attachment = abstractSubmissionRepository.findAttachment(abstractSeq, attachmentSeq);
        if (attachment == null) {
            throw new IllegalArgumentException("발표 자료를 찾을 수 없습니다.");
        }
        if (abstractSubmissionRepository.deleteAttachment(abstractSeq, attachmentSeq) != 1) {
            throw new IllegalArgumentException("발표 자료를 찾을 수 없습니다.");
        }
        deleteStoredFile(attachment.getSaveFilename());
    }

    private AbstractSubmissionResponse requireApprovedAbstract(Long conferenceSeq, Long abstractSeq) {
        AbstractSubmissionResponse submission = abstractSeq == null
                ? null
                : abstractSubmissionRepository.findBySeq(
                        conferenceSeq, abstractSeq, personalDataProperties.requireDbEncString()
                );
        if (submission == null) {
            throw new IllegalArgumentException("초록을 찾을 수 없습니다.");
        }
        if (!"approved".equals(submission.getStatus())) {
            throw new IllegalArgumentException("승인된 초록에만 발표 자료를 등록할 수 있습니다.");
        }
        return submission;
    }

    private void requireMemberUploadAccess(Long conferenceSeq, Long abstractSeq, Long memberSeq) {
        requireMemberAbstractAccess(conferenceSeq, abstractSeq, memberSeq);

        ConferenceSettings conference = conferenceSettingsRepository.findBySeq(conferenceSeq);
        LocalDate today = LocalDate.now(clock);
        if (conference == null
                || conference.getPresentationMaterialStartDate() == null
                || conference.getPresentationMaterialEndDate() == null) {
            throw new IllegalArgumentException("발표 자료 등록기간이 설정되지 않았습니다.");
        }
        if (today.isBefore(conference.getPresentationMaterialStartDate())
                || today.isAfter(conference.getPresentationMaterialEndDate())) {
            throw new IllegalArgumentException("현재 발표 자료 등록기간이 아닙니다.");
        }
    }

    private void requireMemberAbstractAccess(Long conferenceSeq, Long abstractSeq, Long memberSeq) {
        AbstractSubmissionResponse submission = requireApprovedAbstract(conferenceSeq, abstractSeq);
        if (!"member".equals(submission.getSubmissionSource()) || !java.util.Objects.equals(memberSeq, submission.getMemberSeq())) {
            throw new IllegalArgumentException("본인의 승인된 초록에만 발표 자료를 등록할 수 있습니다.");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 발표 자료를 선택하세요.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("발표 자료는 파일당 100MB 이하만 등록할 수 있습니다.");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (originalFilename.isBlank() || originalFilename.contains("..")) {
            throw new IllegalArgumentException("올바른 파일명이 아닙니다.");
        }
        if (originalFilename.length() > 255) {
            throw new IllegalArgumentException("파일명은 255자 이하만 사용할 수 있습니다.");
        }
        String extension = extension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("발표 자료는 PDF, PPT, PPTX 파일만 등록할 수 있습니다.");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_CONTENT_TYPES.get(extension).contains(contentType)) {
            throw new IllegalArgumentException("파일 형식과 확장자가 일치하지 않습니다.");
        }
    }

    private String extension(String filename) {
        String extension = StringUtils.getFilenameExtension(filename);
        return extension == null ? "" : extension.toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        return contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType.toLowerCase(Locale.ROOT);
    }

    private AbstractSubmissionAttachmentResponse toResponse(AbstractSubmissionAttachment attachment) {
        return AbstractSubmissionAttachmentResponse.builder()
                .seq(attachment.getSeq())
                .abstractSeq(attachment.getAbstractSeq())
                .originalFilename(attachment.getOriginalFilename())
                .contentType(attachment.getContentType())
                .fileExtension(attachment.getFileExtension())
                .fileSize(attachment.getFileSize())
                .build();
    }

    private void deleteStoredFile(String savedPath) {
        try {
            Files.deleteIfExists(uploadStorage.resolve(UploadStorage.ABSTRACTS, savedPath));
        } catch (IOException ignored) {
            // DB 상태를 우선 유지하고 고아 파일 정리는 운영 로그/배치에서 처리합니다.
        }
    }
}
