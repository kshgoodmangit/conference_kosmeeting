package com.bjworld21.congress.service;

import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.dto.SponsorshipApplicationPageResponse;
import com.bjworld21.congress.dto.SponsorshipApplicationResponse;
import com.bjworld21.congress.entity.SponsorshipApplication;
import com.bjworld21.congress.repository.SponsorshipApplicationRepository;
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
public class SponsorshipApplicationService {
    private static final Set<String> ALLOWED_LICENSE_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");

    private final SponsorshipApplicationRepository sponsorshipApplicationRepository;
    private final UploadStorage uploadStorage;
    private final PersonalDataProperties personalDataProperties;

    public SponsorshipApplicationService(
            SponsorshipApplicationRepository sponsorshipApplicationRepository,
            UploadStorage uploadStorage,
            PersonalDataProperties personalDataProperties
    ) {
        this.sponsorshipApplicationRepository = sponsorshipApplicationRepository;
        this.uploadStorage = uploadStorage;
        this.personalDataProperties = personalDataProperties;
    }

    public SponsorshipApplicationPageResponse findPage(Long conferenceSeq, int page, int size, String keyword) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedKeyword = normalize(keyword);
        long totalCount = sponsorshipApplicationRepository.countByKeyword(conferenceSeq, normalizedKeyword, dbEncString());
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / safeSize));
        int adjustedPage = Math.min(safePage, totalPages);
        int offset = (adjustedPage - 1) * safeSize;

        return SponsorshipApplicationPageResponse.builder()
                .items(sponsorshipApplicationRepository.findPage(conferenceSeq, normalizedKeyword, safeSize, offset, dbEncString()).stream()
                        .map(this::toResponse)
                        .toList())
                .page(adjustedPage)
                .size(safeSize)
                .totalCount(totalCount)
                .depositedCount(sponsorshipApplicationRepository.countByDeposited(conferenceSeq, normalizedKeyword, true, dbEncString()))
                .pendingCount(sponsorshipApplicationRepository.countByDeposited(conferenceSeq, normalizedKeyword, false, dbEncString()))
                .totalAmount(sponsorshipApplicationRepository.sumAmountByKeyword(conferenceSeq, normalizedKeyword, dbEncString()))
                .totalPages(totalPages)
                .build();
    }

    public SponsorshipApplicationResponse getBySeq(Long conferenceSeq, Long seq) {
        return toResponse(findApplication(conferenceSeq, seq));
    }

    public SponsorshipApplicationResponse create(Long conferenceSeq, SponsorshipApplication application, MultipartFile businessLicenseFile) {
        application.setConferenceSeq(conferenceSeq);
        normalizeApplication(application);
        validate(application);
        if (sponsorshipApplicationRepository.findByBusinessNumber(conferenceSeq, application.getBusinessNumber(), dbEncString()) != null) {
            throw new IllegalArgumentException("이미 등록된 사업자등록번호입니다.");
        }

        storeBusinessLicenseIfPresent(application, businessLicenseFile, null);
        sponsorshipApplicationRepository.insert(conferenceSeq, application, dbEncString());
        return getBySeq(conferenceSeq, application.getSeq());
    }

    public SponsorshipApplicationResponse update(Long conferenceSeq, Long seq, SponsorshipApplication application, MultipartFile businessLicenseFile) {
        SponsorshipApplication existing = findApplication(conferenceSeq, seq);
        application.setSeq(seq);
        application.setBusinessLicenseOriFilename(existing.getBusinessLicenseOriFilename());
        application.setBusinessLicenseSaveFilename(existing.getBusinessLicenseSaveFilename());
        normalizeApplication(application);
        validate(application);

        SponsorshipApplication duplicate = sponsorshipApplicationRepository.findByBusinessNumberExceptSeq(
                conferenceSeq, application.getBusinessNumber(), seq, dbEncString()
        );
        if (duplicate != null) {
            throw new IllegalArgumentException("이미 등록된 사업자등록번호입니다.");
        }

        storeBusinessLicenseIfPresent(application, businessLicenseFile, existing.getBusinessLicenseSaveFilename());
        sponsorshipApplicationRepository.update(conferenceSeq, application, dbEncString());
        return getBySeq(conferenceSeq, seq);
    }

    public void delete(Long conferenceSeq, Long seq) {
        SponsorshipApplication application = findApplication(conferenceSeq, seq);
        sponsorshipApplicationRepository.delete(conferenceSeq, seq);
        deleteStoredFile(application.getBusinessLicenseSaveFilename());
    }

    public Resource getBusinessLicense(Long conferenceSeq, Long seq) {
        SponsorshipApplication application = findApplication(conferenceSeq, seq);
        if (application.getBusinessLicenseSaveFilename() == null || application.getBusinessLicenseSaveFilename().isBlank()) {
            throw new IllegalArgumentException("등록된 사업자등록증 파일이 없습니다.");
        }

        try {
            Path filePath = uploadStorage.resolve(
                    UploadStorage.SPONSORSHIPS,
                    application.getBusinessLicenseSaveFilename()
            );
            if (!Files.exists(filePath)) {
                throw new IllegalArgumentException("사업자등록증 파일을 찾을 수 없습니다.");
            }
            return new UrlResource(filePath.toUri());
        } catch (MalformedURLException e) {
            throw new IllegalStateException("사업자등록증 파일을 읽을 수 없습니다.", e);
        }
    }

    private SponsorshipApplication findApplication(Long conferenceSeq, Long seq) {
        SponsorshipApplication application = sponsorshipApplicationRepository.findBySeq(conferenceSeq, seq, dbEncString());
        if (application == null) {
            throw new IllegalArgumentException("존재하지 않는 후원 신청입니다.");
        }
        return application;
    }

    private void validate(SponsorshipApplication application) {
        require(application.getCompanyKrName(), "회사명(국문)은 필수입니다.");
        require(application.getCeoName(), "대표자명은 필수입니다.");
        require(application.getBusinessNumber(), "사업자등록번호는 필수입니다.");
        require(application.getZonecode(), "우편번호는 필수입니다.");
        require(application.getAddress(), "주소는 필수입니다.");
        require(application.getAddressDetail(), "상세 주소는 필수입니다.");
        require(application.getSponsorshipType(), "후원 구분은 필수입니다.");
        require(application.getContactPersonName(), "담당자 이름은 필수입니다.");
        require(application.getContactPersonPhone(), "담당자 전화는 필수입니다.");
        require(application.getContactPersonMobile(), "담당자 휴대전화는 필수입니다.");
        require(application.getContactPersonEmail(), "담당자 이메일은 필수입니다.");

        if (application.getSponsorshipAmount() != null && application.getSponsorshipAmount() < 0) {
            throw new IllegalArgumentException("후원 금액은 0원 이상이어야 합니다.");
        }
        if (Boolean.FALSE.equals(application.getIsDeposited())) {
            application.setDepositDate(null);
        }
    }

    private void storeBusinessLicenseIfPresent(SponsorshipApplication application, MultipartFile file, String previousSavedFilename) {
        if (file == null || file.isEmpty()) {
            return;
        }

        StoredFile storedFile = storeFile(file);
        deleteStoredFile(previousSavedFilename);
        application.setBusinessLicenseOriFilename(storedFile.originalFilename());
        application.setBusinessLicenseSaveFilename(storedFile.savedFilename());
    }

    private StoredFile storeFile(MultipartFile file) {
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String extension = getExtension(originalFilename);
        if (!ALLOWED_LICENSE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("사업자등록증은 pdf, jpg, jpeg, png 파일만 업로드할 수 있습니다.");
        }

        UploadStorage.StoredTarget storedTarget = uploadStorage.monthlyTarget(
                UploadStorage.SPONSORSHIPS,
                UUID.randomUUID() + "." + extension
        );
        String savedFilename = storedTarget.relativePath();
        try {
            Path targetPath = storedTarget.path();
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath);
            return new StoredFile(originalFilename, savedFilename);
        } catch (IOException e) {
            throw new IllegalStateException("사업자등록증 저장에 실패했습니다.", e);
        }
    }

    private void deleteStoredFile(String savedFilename) {
        if (savedFilename == null || savedFilename.isBlank()) {
            return;
        }
        try {
            Path targetPath = uploadStorage.resolve(UploadStorage.SPONSORSHIPS, savedFilename);
            Files.deleteIfExists(targetPath);
        } catch (IOException ignored) {
            // The database change should not be blocked by file cleanup.
        }
    }

    private void normalizeApplication(SponsorshipApplication application) {
        application.setCompanyKrName(normalize(application.getCompanyKrName()));
        application.setCompanyEnName(normalize(application.getCompanyEnName()));
        application.setCeoName(normalize(application.getCeoName()));
        application.setBusinessNumber(normalize(application.getBusinessNumber()));
        application.setZonecode(normalize(application.getZonecode()));
        application.setAddress(normalize(application.getAddress()));
        application.setAddressDetail(normalize(application.getAddressDetail()));
        application.setSponsorshipType(normalize(application.getSponsorshipType()));
        application.setSponsorshipAmount(application.getSponsorshipAmount() == null ? 0L : application.getSponsorshipAmount());
        application.setContactPersonName(normalize(application.getContactPersonName()));
        application.setContactPersonPosition(normalize(application.getContactPersonPosition()));
        application.setContactPersonDepartment(normalize(application.getContactPersonDepartment()));
        application.setContactPersonPhone(normalize(application.getContactPersonPhone()));
        application.setContactPersonMobile(normalize(application.getContactPersonMobile()));
        application.setContactPersonEmail(normalize(application.getContactPersonEmail()));
        application.setFaxNumber(normalize(application.getFaxNumber()));
        application.setIsDeposited(Boolean.TRUE.equals(application.getIsDeposited()));
        application.setTaxInvoiceRecipient(normalize(application.getTaxInvoiceRecipient()));
        application.setTaxInvoiceEmail(normalize(application.getTaxInvoiceEmail()));
        application.setTaxInvoiceType(normalize(application.getTaxInvoiceType()));
        application.setRemarks(normalize(application.getRemarks()));
    }

    private void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private SponsorshipApplicationResponse toResponse(SponsorshipApplication application) {
        String businessLicenseUrl = application.getBusinessLicenseSaveFilename() == null || application.getBusinessLicenseSaveFilename().isBlank()
                ? null
                : "/api/admin/sponsorship-applications/" + application.getSeq() + "/business-license";

        return SponsorshipApplicationResponse.builder()
                .seq(application.getSeq())
                .companyKrName(application.getCompanyKrName())
                .companyEnName(application.getCompanyEnName())
                .ceoName(application.getCeoName())
                .businessNumber(application.getBusinessNumber())
                .zonecode(application.getZonecode())
                .address(application.getAddress())
                .addressDetail(application.getAddressDetail())
                .sponsorshipType(application.getSponsorshipType())
                .sponsorshipAmount(application.getSponsorshipAmount())
                .businessLicenseOriFilename(application.getBusinessLicenseOriFilename())
                .businessLicenseSaveFilename(application.getBusinessLicenseSaveFilename())
                .businessLicenseUrl(businessLicenseUrl)
                .contactPersonName(application.getContactPersonName())
                .contactPersonPosition(application.getContactPersonPosition())
                .contactPersonDepartment(application.getContactPersonDepartment())
                .contactPersonPhone(application.getContactPersonPhone())
                .contactPersonMobile(application.getContactPersonMobile())
                .contactPersonEmail(application.getContactPersonEmail())
                .faxNumber(application.getFaxNumber())
                .isDeposited(application.getIsDeposited())
                .depositDate(application.getDepositDate())
                .expectedDepositDate(application.getExpectedDepositDate())
                .taxInvoiceRecipient(application.getTaxInvoiceRecipient())
                .taxInvoiceEmail(application.getTaxInvoiceEmail())
                .taxInvoiceIssueDate(application.getTaxInvoiceIssueDate())
                .taxInvoiceType(application.getTaxInvoiceType())
                .remarks(application.getRemarks())
                .createdAt(application.getCreatedAt())
                .updatedAt(application.getUpdatedAt())
                .build();
    }

    private record StoredFile(String originalFilename, String savedFilename) {
    }
    private String dbEncString() {
        return personalDataProperties.requireDbEncString();
    }
}
