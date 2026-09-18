package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.PopupPageResponse;
import com.bjworld21.congress.dto.PopupResponse;
import com.bjworld21.congress.entity.Popup;
import com.bjworld21.congress.repository.PopupRepository;
import org.jsoup.Jsoup;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PopupService {
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private final PopupRepository popupRepository;
    private final UploadStorage uploadStorage;
    private final CmsHtmlSanitizer htmlSanitizer;
    private final WebRiskLinkValidator webRiskLinkValidator;

    public PopupService(PopupRepository popupRepository, UploadStorage uploadStorage, CmsHtmlSanitizer htmlSanitizer,
                        WebRiskLinkValidator webRiskLinkValidator) {
        this.popupRepository = popupRepository;
        this.uploadStorage = uploadStorage;
        this.htmlSanitizer = htmlSanitizer;
        this.webRiskLinkValidator = webRiskLinkValidator;
    }

    public PopupPageResponse findPage(Long conferenceSeq, int page, int size, String keyword) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        long totalCount = popupRepository.countByKeyword(conferenceSeq, normalizedKeyword);
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / safeSize));
        int adjustedPage = Math.min(safePage, totalPages);
        int offset = (adjustedPage - 1) * safeSize;

        return PopupPageResponse.builder()
                .items(popupRepository.findPage(conferenceSeq, normalizedKeyword, safeSize, offset).stream()
                        .map(this::toResponse)
                        .toList())
                .page(adjustedPage)
                .size(safeSize)
                .totalCount(totalCount)
                .enabledCount(popupRepository.countEnabledByKeyword(conferenceSeq, normalizedKeyword))
                .totalPages(totalPages)
                .build();
    }

    public PopupResponse getBySeq(Long conferenceSeq, Long seq) {
        Popup popup = findPopup(conferenceSeq, seq);
        return toResponse(popup);
    }

    public PopupResponse create(Long conferenceSeq, String title, String linkUrl, Boolean enabled, LocalDate useStartDate, LocalDate useEndDate, String content, MultipartFile imageFile) {
        validate(title, useStartDate, useEndDate);
        String sanitizedContent = htmlSanitizer.sanitize(content);
        String normalizedLinkUrl = normalize(linkUrl);
        String webRiskWarning = webRiskLinkValidator.validate(sanitizedContent, normalizedLinkUrl);

        Popup popup = Popup.builder()
                .conferenceSeq(conferenceSeq)
                .title(title.trim())
                .linkUrl(normalizedLinkUrl)
                .enabled(enabled != null ? enabled : Boolean.FALSE)
                .useStartDate(useStartDate)
                .useEndDate(useEndDate)
                .content(sanitizedContent)
                .build();

        if (imageFile != null && !imageFile.isEmpty()) {
            StoredFile storedFile = storeImage(imageFile);
            popup.setPopupImageOriFilename(storedFile.originalFilename());
            popup.setPopupImageSaveFilename(storedFile.savedFilename());
        }

        popupRepository.insert(popup);
        PopupResponse response = getBySeq(conferenceSeq, popup.getSeq());
        response.setWebRiskWarning(webRiskWarning);
        return response;
    }

    public PopupResponse update(Long conferenceSeq, Long seq, String title, String linkUrl, Boolean enabled, LocalDate useStartDate, LocalDate useEndDate, String content, MultipartFile imageFile) {
        Popup popup = findPopup(conferenceSeq, seq);
        validate(title, useStartDate, useEndDate);
        String sanitizedContent = htmlSanitizer.sanitize(content);
        String normalizedLinkUrl = normalize(linkUrl);
        String webRiskWarning = webRiskLinkValidator.validate(sanitizedContent, normalizedLinkUrl);

        popup.setTitle(title.trim());
        popup.setLinkUrl(normalizedLinkUrl);
        popup.setEnabled(enabled != null ? enabled : Boolean.FALSE);
        popup.setUseStartDate(useStartDate);
        popup.setUseEndDate(useEndDate);
        popup.setContent(sanitizedContent);

        if (imageFile != null && !imageFile.isEmpty()) {
            deleteStoredFile(popup.getPopupImageSaveFilename());
            StoredFile storedFile = storeImage(imageFile);
            popup.setPopupImageOriFilename(storedFile.originalFilename());
            popup.setPopupImageSaveFilename(storedFile.savedFilename());
        }

        popupRepository.update(popup);
        PopupResponse response = getBySeq(conferenceSeq, seq);
        response.setWebRiskWarning(webRiskWarning);
        return response;
    }

    public void delete(Long conferenceSeq, Long seq) {
        Popup popup = findPopup(conferenceSeq, seq);
        popupRepository.delete(conferenceSeq, seq);
        deleteStoredFile(popup.getPopupImageSaveFilename());
    }

    public Resource getImage(Long conferenceSeq, Long seq) {
        Popup popup = findPopup(conferenceSeq, seq);
        if (popup.getPopupImageSaveFilename() == null || popup.getPopupImageSaveFilename().isBlank()) {
            throw new IllegalArgumentException("등록된 팝업 이미지가 없습니다.");
        }

        try {
            Path imagePath = uploadStorage.resolve(UploadStorage.POPUPS, popup.getPopupImageSaveFilename());
            if (!Files.exists(imagePath)) {
                throw new IllegalArgumentException("팝업 이미지 파일을 찾을 수 없습니다.");
            }
            return new UrlResource(imagePath.toUri());
        } catch (MalformedURLException e) {
            throw new IllegalStateException("팝업 이미지 파일을 읽을 수 없습니다.", e);
        }
    }

    private Popup findPopup(Long conferenceSeq, Long seq) {
        Popup popup = popupRepository.findBySeq(conferenceSeq, seq);
        if (popup == null) {
            throw new IllegalArgumentException("존재하지 않는 팝업입니다.");
        }
        return popup;
    }

    private void validate(String title, LocalDate useStartDate, LocalDate useEndDate) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("팝업 제목은 필수입니다.");
        }
        if (useStartDate != null && useEndDate != null && useEndDate.isBefore(useStartDate)) {
            throw new IllegalArgumentException("사용 종료일은 시작일보다 빠를 수 없습니다.");
        }
    }

    private StoredFile storeImage(MultipartFile file) {
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String extension = getExtension(originalFilename);
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("팝업 이미지는 jpg, jpeg, png, gif, webp 파일만 업로드할 수 있습니다.");
        }

        UploadStorage.StoredTarget storedTarget = uploadStorage.monthlyTarget(
                UploadStorage.POPUPS,
                UUID.randomUUID() + "." + extension
        );
        String savedFilename = storedTarget.relativePath();
        try {
            Path targetPath = storedTarget.path();
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
            return new StoredFile(originalFilename, savedFilename);
        } catch (IOException e) {
            throw new IllegalStateException("팝업 이미지 저장에 실패했습니다.", e);
        }
    }

    private void deleteStoredFile(String savedFilename) {
        if (savedFilename == null || savedFilename.isBlank()) {
            return;
        }
        try {
            Path targetPath = uploadStorage.resolve(UploadStorage.POPUPS, savedFilename);
            Files.deleteIfExists(targetPath);
        } catch (IOException ignored) {
            // Database state should not be blocked by stale file cleanup.
        }
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private PopupResponse toResponse(Popup popup) {
        String imageUrl = firstContentImageUrl(popup.getContent());
        if (imageUrl == null && popup.getPopupImageSaveFilename() != null && !popup.getPopupImageSaveFilename().isBlank()) {
            imageUrl = "/api/popups/" + popup.getSeq() + "/image";
        }

        return PopupResponse.builder()
                .seq(popup.getSeq())
                .title(popup.getTitle())
                .linkUrl(popup.getLinkUrl())
                .enabled(popup.getEnabled())
                .useStartDate(popup.getUseStartDate())
                .useEndDate(popup.getUseEndDate())
                .content(popup.getContent())
                .popupImageOriFilename(popup.getPopupImageOriFilename())
                .popupImageSaveFilename(popup.getPopupImageSaveFilename())
                .imageUrl(imageUrl)
                .createdAt(popup.getCreatedAt())
                .updatedAt(popup.getUpdatedAt())
                .build();
    }

    private String firstContentImageUrl(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String source = Jsoup.parse(content).select("img[src]").attr("src").trim();
        return source.isEmpty() ? null : source;
    }

    private record StoredFile(String originalFilename, String savedFilename) {
    }
}
