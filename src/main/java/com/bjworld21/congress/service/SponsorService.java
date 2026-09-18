package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.SponsorPageResponse;
import com.bjworld21.congress.dto.SponsorResponse;
import com.bjworld21.congress.dto.SponsorTypeResponse;
import com.bjworld21.congress.entity.CommonCode;
import com.bjworld21.congress.entity.Sponsor;
import com.bjworld21.congress.repository.SponsorRepository;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class SponsorService {
    private static final long MAX_LOGO_SIZE = 2L * 1024L * 1024L;
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final SponsorRepository sponsorRepository;
    private final UploadStorage uploadStorage;

    public SponsorService(SponsorRepository sponsorRepository, UploadStorage uploadStorage) {
        this.sponsorRepository = sponsorRepository;
        this.uploadStorage = uploadStorage;
    }

    public SponsorPageResponse findPage(Long conferenceSeq, int page, int size, String keyword) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        long totalCount = sponsorRepository.countByKeyword(conferenceSeq, normalizedKeyword);
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / safeSize));
        int adjustedPage = Math.min(safePage, totalPages);
        int offset = (adjustedPage - 1) * safeSize;

        return SponsorPageResponse.builder()
                .items(sponsorRepository.findPage(conferenceSeq, normalizedKeyword, safeSize, offset).stream()
                        .map(this::toResponse)
                        .toList())
                .page(adjustedPage)
                .size(safeSize)
                .totalCount(totalCount)
                .enabledCount(sponsorRepository.countEnabledByKeyword(conferenceSeq, normalizedKeyword))
                .totalPages(totalPages)
                .build();
    }

    public List<SponsorTypeResponse> findSponsorTypes() {
        return sponsorRepository.findActiveSponsorTypes().stream()
                .map(code -> SponsorTypeResponse.builder()
                        .seq(code.getSeq())
                        .codeName(code.getCodeName())
                        .sortOrder(code.getSortOrder())
                        .build())
                .toList();
    }

    public List<SponsorResponse> findVisible(Long conferenceSeq) {
        return sponsorRepository.findVisible(conferenceSeq).stream()
                .map(this::toResponse)
                .toList();
    }

    public SponsorResponse getBySeq(Long conferenceSeq, Long seq) {
        return toResponse(findSponsor(conferenceSeq, seq));
    }

    public SponsorResponse create(
            Long conferenceSeq,
            Long sponsorTypeCode,
            String sponsorName,
            String linkUrl,
            LocalDate useStartDate,
            LocalDate useEndDate,
            Boolean enabled,
            Integer sortOrder,
            MultipartFile logoFile
    ) {
        validate(sponsorTypeCode, sponsorName, linkUrl, useStartDate, useEndDate, sortOrder);
        if (logoFile == null || logoFile.isEmpty()) {
            throw new IllegalArgumentException("스폰서 로고는 필수입니다.");
        }

        StoredFile storedFile = storeLogo(logoFile);
        Sponsor sponsor = Sponsor.builder()
                .conferenceSeq(conferenceSeq)
                .sponsorTypeCode(sponsorTypeCode)
                .sponsorName(sponsorName.trim())
                .linkUrl(normalizeLinkUrl(linkUrl))
                .logoOriFilename(storedFile.originalFilename())
                .logoSaveFilename(storedFile.savedFilename())
                .useStartDate(useStartDate)
                .useEndDate(useEndDate)
                .enabled(enabled != null ? enabled : Boolean.TRUE)
                .sortOrder(sortOrder != null ? sortOrder : 0)
                .build();

        try {
            sponsorRepository.insert(sponsor);
        } catch (RuntimeException exception) {
            deleteStoredFile(storedFile.savedFilename());
            throw exception;
        }
        return getBySeq(conferenceSeq, sponsor.getSeq());
    }

    public SponsorResponse update(
            Long conferenceSeq,
            Long seq,
            Long sponsorTypeCode,
            String sponsorName,
            String linkUrl,
            LocalDate useStartDate,
            LocalDate useEndDate,
            Boolean enabled,
            Integer sortOrder,
            MultipartFile logoFile
    ) {
        Sponsor sponsor = findSponsor(conferenceSeq, seq);
        validate(sponsorTypeCode, sponsorName, linkUrl, useStartDate, useEndDate, sortOrder);

        String previousSavedFilename = sponsor.getLogoSaveFilename();
        StoredFile newStoredFile = null;
        if (logoFile != null && !logoFile.isEmpty()) {
            newStoredFile = storeLogo(logoFile);
            sponsor.setLogoOriFilename(newStoredFile.originalFilename());
            sponsor.setLogoSaveFilename(newStoredFile.savedFilename());
        }

        sponsor.setSponsorTypeCode(sponsorTypeCode);
        sponsor.setSponsorName(sponsorName.trim());
        sponsor.setLinkUrl(normalizeLinkUrl(linkUrl));
        sponsor.setUseStartDate(useStartDate);
        sponsor.setUseEndDate(useEndDate);
        sponsor.setEnabled(enabled != null ? enabled : Boolean.TRUE);
        sponsor.setSortOrder(sortOrder != null ? sortOrder : 0);

        try {
            sponsorRepository.update(sponsor);
        } catch (RuntimeException exception) {
            if (newStoredFile != null) {
                deleteStoredFile(newStoredFile.savedFilename());
            }
            throw exception;
        }

        if (newStoredFile != null) {
            deleteStoredFile(previousSavedFilename);
        }
        return getBySeq(conferenceSeq, seq);
    }

    public void delete(Long conferenceSeq, Long seq) {
        Sponsor sponsor = findSponsor(conferenceSeq, seq);
        sponsorRepository.delete(conferenceSeq, seq);
        deleteStoredFile(sponsor.getLogoSaveFilename());
    }

    public Resource getLogo(Long conferenceSeq, Long seq) {
        Sponsor sponsor = findSponsor(conferenceSeq, seq);
        if (sponsor.getLogoSaveFilename() == null || sponsor.getLogoSaveFilename().isBlank()) {
            throw new IllegalArgumentException("등록된 스폰서 로고가 없습니다.");
        }

        try {
            Path logoPath = uploadStorage.resolve(UploadStorage.SPONSORS, sponsor.getLogoSaveFilename());
            if (!Files.exists(logoPath)) {
                throw new IllegalArgumentException("스폰서 로고 파일을 찾을 수 없습니다.");
            }
            return new UrlResource(logoPath.toUri());
        } catch (MalformedURLException exception) {
            throw new IllegalStateException("스폰서 로고 파일을 읽을 수 없습니다.", exception);
        }
    }

    private Sponsor findSponsor(Long conferenceSeq, Long seq) {
        Sponsor sponsor = seq == null ? null : sponsorRepository.findBySeq(conferenceSeq, seq);
        if (sponsor == null) {
            throw new IllegalArgumentException("존재하지 않는 스폰서입니다.");
        }
        return sponsor;
    }

    private void validate(
            Long sponsorTypeCode,
            String sponsorName,
            String linkUrl,
            LocalDate useStartDate,
            LocalDate useEndDate,
            Integer sortOrder
    ) {
        CommonCode sponsorType = sponsorTypeCode == null
                ? null
                : sponsorRepository.findActiveSponsorType(sponsorTypeCode);
        if (sponsorType == null) {
            throw new IllegalArgumentException("유효한 스폰서 구분을 선택해주세요.");
        }
        if (sponsorName == null || sponsorName.trim().isEmpty()) {
            throw new IllegalArgumentException("스폰서 이름은 필수입니다.");
        }
        if (sponsorName.trim().length() > 150) {
            throw new IllegalArgumentException("스폰서 이름은 150자 이하로 입력해주세요.");
        }
        normalizeLinkUrl(linkUrl);
        if (useStartDate != null && useEndDate != null && useEndDate.isBefore(useStartDate)) {
            throw new IllegalArgumentException("사용 종료일은 시작일보다 빠를 수 없습니다.");
        }
        if (sortOrder != null && sortOrder < 0) {
            throw new IllegalArgumentException("노출 순서는 0 이상이어야 합니다.");
        }
    }

    private String normalizeLinkUrl(String linkUrl) {
        if (linkUrl == null || linkUrl.trim().isEmpty()) {
            return null;
        }

        String normalized = linkUrl.trim();
        if (normalized.length() > 1000) {
            throw new IllegalArgumentException("링크 URL은 1000자 이하로 입력해주세요.");
        }
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            if (scheme == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException("링크 URL은 http:// 또는 https://로 시작하는 주소여야 합니다.");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("링크 URL 형식이 올바르지 않습니다.");
        }
        return normalized;
    }

    private StoredFile storeLogo(MultipartFile file) {
        if (file.getSize() > MAX_LOGO_SIZE) {
            throw new IllegalArgumentException("스폰서 로고는 2MB 이하만 업로드할 수 있습니다.");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String extension = getExtension(originalFilename);
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("스폰서 로고는 jpg, jpeg, png, webp 파일만 업로드할 수 있습니다.");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank() && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("이미지 형식의 스폰서 로고만 업로드할 수 있습니다.");
        }

        UploadStorage.StoredTarget storedTarget = uploadStorage.monthlyTarget(
                UploadStorage.SPONSORS,
                UUID.randomUUID() + "." + extension
        );
        String savedFilename = storedTarget.relativePath();
        try {
            Path targetPath = storedTarget.path();
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
            return new StoredFile(originalFilename, savedFilename);
        } catch (IOException exception) {
            throw new IllegalStateException("스폰서 로고 저장에 실패했습니다.", exception);
        }
    }

    private void deleteStoredFile(String savedFilename) {
        if (savedFilename == null || savedFilename.isBlank()) {
            return;
        }
        try {
            Path targetPath = uploadStorage.resolve(UploadStorage.SPONSORS, savedFilename);
            Files.deleteIfExists(targetPath);
        } catch (IOException ignored) {
            // DB 처리를 완료한 뒤 남은 파일 정리는 다음 관리 작업을 막지 않습니다.
        }
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private SponsorResponse toResponse(Sponsor sponsor) {
        String logoUrl = sponsor.getLogoSaveFilename() == null || sponsor.getLogoSaveFilename().isBlank()
                ? null
                : "/api/public/" + sponsor.getConferenceSeq() + "/sponsors/" + sponsor.getSeq() + "/logo";

        return SponsorResponse.builder()
                .seq(sponsor.getSeq())
                .sponsorTypeCode(sponsor.getSponsorTypeCode())
                .sponsorTypeName(sponsor.getSponsorTypeName())
                .sponsorName(sponsor.getSponsorName())
                .linkUrl(sponsor.getLinkUrl())
                .logoOriFilename(sponsor.getLogoOriFilename())
                .logoUrl(logoUrl)
                .useStartDate(sponsor.getUseStartDate())
                .useEndDate(sponsor.getUseEndDate())
                .enabled(sponsor.getEnabled())
                .sortOrder(sponsor.getSortOrder())
                .createdAt(sponsor.getCreatedAt())
                .updatedAt(sponsor.getUpdatedAt())
                .build();
    }

    private record StoredFile(String originalFilename, String savedFilename) {
    }
}
