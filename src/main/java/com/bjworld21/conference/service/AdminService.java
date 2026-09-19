package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PersonalDataProperties;

import com.bjworld21.conference.dto.AdminCreateRequest;
import com.bjworld21.conference.dto.AdminLoginRequest;
import com.bjworld21.conference.dto.AdminLoginResponse;
import com.bjworld21.conference.dto.AdminPageResponse;
import com.bjworld21.conference.dto.AdminResponse;
import com.bjworld21.conference.dto.AdminPasswordChangeRequest;
import com.bjworld21.conference.dto.AbstractCategoryResponse;
import com.bjworld21.conference.dto.ReviewerOwnProfileResponse;
import com.bjworld21.conference.dto.ReviewerOwnProfileUpdateRequest;
import com.bjworld21.conference.dto.ReviewerProfileRequest;
import com.bjworld21.conference.dto.ReviewerProfileResponse;
import com.bjworld21.conference.dto.TestReviewerCreateResponse;
import com.bjworld21.conference.entity.AdminAccount;
import com.bjworld21.conference.entity.ReviewerProfile;
import com.bjworld21.conference.repository.AdminAccountRepository;
import com.bjworld21.conference.repository.ReviewerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class AdminService {
    private static final char[] PASSWORD_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
    private static final char[] PASSWORD_DIGITS = "23456789".toCharArray();
    private static final char[] PASSWORD_SPECIALS = "!@#$%^&*".toCharArray();
    private static final char[] PASSWORD_ALPHANUMERIC = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private AdminAccountRepository adminAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ReviewerRepository reviewerRepository;

    @Autowired
    private PersonalDataProperties personalDataProperties;

    @Autowired
    private AdminCredentialVerifier adminCredentialVerifier;

    /**
     * 관리자 로그인
     */
    public AdminLoginResponse login(AdminLoginRequest request) {
        // 1. 유효성 검사
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("아이디는 필수입니다.");
        }

        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            throw new IllegalArgumentException("비밀번호는 필수입니다.");
        }

        AdminAccount admin = adminCredentialVerifier.verifyLogin(request.getEmail(), request.getPassword());

        Long reviewerConferenceSeq = null;
        if ("reviewer".equals(admin.getRole())) {
            reviewerConferenceSeq = requireCurrentReviewerConferenceSeq(admin.getSeq());
        }

        // 5. 마지막 로그인 시간 업데이트
        adminAccountRepository.updateLastLoginAt(admin.getSeq());

        // 6. 응답 반환
        return AdminLoginResponse.builder()
                .seq(admin.getSeq())
                .email(admin.getEmail())
                .adminName(admin.getAdminName())
                .role(admin.getRole())
                .conferenceSeq(reviewerConferenceSeq)
                .message("로그인 성공")
                .build();
    }

    public Long requireCurrentReviewerConferenceSeq(Long adminSeq) {
        Long conferenceSeq = reviewerRepository.findCurrentConferenceSeqByAdminSeq(adminSeq);
        if (conferenceSeq != null) {
            return conferenceSeq;
        }

        if (reviewerRepository.countActiveByAdminSeq(adminSeq) > 0) {
            throw new IllegalArgumentException(
                    "종료된 학술대회의 심사자 계정입니다. 현재 진행 중인 학술대회 심사자만 로그인할 수 있습니다."
            );
        }

        throw new IllegalArgumentException("사용할 수 없는 Reviewer 계정입니다.");
    }

    /**
     * 관리자 생성 (최고관리자용)
     */
    @Transactional
    public AdminResponse create(Long conferenceSeq, AdminCreateRequest request) {
        // 1. 유효성 검사
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("아이디는 필수입니다.");
        }

        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new IllegalArgumentException("비밀번호는 최소 8자 이상이어야 합니다.");
        }

        if (request.getAdminName() == null || request.getAdminName().trim().isEmpty()) {
            throw new IllegalArgumentException("관리자 이름은 필수입니다.");
        }

        // 2. 중복 확인
        AdminAccount existingAdmin = adminAccountRepository.findByEmail(request.getEmail(), dbEncString());
        if (existingAdmin != null) {
            throw new IllegalArgumentException("이미 등록된 아이디입니다.");
        }

        // 3. 권한 검증
        String role = request.getRole();
        if (!"admin".equals(role) && !"reviewer".equals(role) && !"maintenance".equals(role)) {
            role = "admin"; // 기본값
        }

        // 4. 관리자 계정 생성
        ReviewerProfileRequest requestedReviewerProfile = request.getReviewerProfile();
        AdminAccount adminAccount = AdminAccount.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .adminName(request.getAdminName())
                .role(role)
                .status("active")
                .build();
        applyRequiredContactInfo(
                adminAccount,
                valueOrExisting(request.getAffiliation(), requestedReviewerProfile == null ? null : requestedReviewerProfile.getAffiliation()),
                valueOrExisting(request.getDepartment(), requestedReviewerProfile == null ? null : requestedReviewerProfile.getDepartment()),
                valueOrExisting(request.getPositionTitle(), requestedReviewerProfile == null ? null : requestedReviewerProfile.getPositionTitle()),
                valueOrExisting(request.getPhoneNumber(), requestedReviewerProfile == null ? null : requestedReviewerProfile.getPhoneNumber()),
                valueOrExisting(request.getContactEmail(), requestedReviewerProfile == null ? null : requestedReviewerProfile.getContactEmail())
        );

        adminAccountRepository.insert(adminAccount, dbEncString());

        ReviewerProfileResponse reviewerProfile = null;
        if ("reviewer".equals(role)) {
            reviewerProfile = saveReviewerProfile(
                    conferenceSeq,
                    adminAccount.getSeq(),
                    reviewerProfileWithContactInfo(adminAccount, requestedReviewerProfile)
            );
        }

        // 5. 응답 반환
        return AdminResponse.builder()
                .seq(adminAccount.getSeq())
                .email(adminAccount.getEmail())
                .adminName(adminAccount.getAdminName())
                .affiliation(adminAccount.getAffiliation())
                .department(adminAccount.getDepartment())
                .positionTitle(adminAccount.getPositionTitle())
                .phoneNumber(adminAccount.getPhoneNumber())
                .contactEmail(adminAccount.getContactEmail())
                .role(adminAccount.getRole())
                .status(adminAccount.getStatus())
                .reviewerProfile(reviewerProfile)
                .message("관리자 계정이 생성되었습니다.")
                .build();
    }

    public boolean isAdminIdAvailable(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("아이디를 입력해주세요.");
        }
        return adminAccountRepository.findByEmail(email.trim(), dbEncString()) == null;
    }

    @Transactional
    public TestReviewerCreateResponse createTestReviewerAccounts(Long conferenceSeq) {
        List<AbstractCategoryResponse> categories = reviewerRepository.findEnabledCategories();
        List<String> createdAccounts = new ArrayList<>();
        int skippedCount = 0;
        String[] affiliations = {"ICMS 테스트 병원", "ICMS 테스트 대학", "ICMS 테스트 연구소"};
        String[] departments = {"내과", "외과", "영상의학과", "병리과", "연구개발팀"};

        for (int index = 1; index <= 10; index++) {
            String accountId = "reviewer" + index;
            if (adminAccountRepository.findByEmail(accountId, dbEncString()) != null) {
                skippedCount++;
                continue;
            }

            List<String> expertiseCodes = categories.isEmpty()
                    ? List.of()
                    : List.of(String.valueOf(categories.get((index - 1) % categories.size()).getCode()));
            ReviewerProfileRequest reviewerProfile = ReviewerProfileRequest.builder()
                    .affiliation(affiliations[(index - 1) % affiliations.length])
                    .department(departments[(index - 1) % departments.length])
                    .positionTitle("테스트 심사위원")
                    .phoneNumber("010-0000-%04d".formatted(index))
                    .contactEmail(accountId + "@example.com")
                    .isUsed("Y")
                    .expertiseCodes(expertiseCodes)
                    .build();

            create(conferenceSeq, AdminCreateRequest.builder()
                    .email(accountId)
                    .password("reviewer12#$")
                    .adminName("테스트 심사자 " + index)
                    .affiliation(reviewerProfile.getAffiliation())
                    .department(reviewerProfile.getDepartment())
                    .positionTitle(reviewerProfile.getPositionTitle())
                    .phoneNumber(reviewerProfile.getPhoneNumber())
                    .contactEmail(reviewerProfile.getContactEmail())
                    .role("reviewer")
                    .reviewerProfile(reviewerProfile)
                    .build());
            createdAccounts.add(accountId);
        }

        return TestReviewerCreateResponse.builder()
                .createdCount(createdAccounts.size())
                .skippedCount(skippedCount)
                .createdAccounts(createdAccounts)
                .build();
    }

    /**
     * 관리자 정보 조회
     */
    public AdminResponse getAdmin(Long conferenceSeq, Long seq) {
        AdminAccount admin = adminAccountRepository.findBySeq(seq, dbEncString());
        if (admin == null) {
            throw new IllegalArgumentException("존재하지 않는 관리자입니다.");
        }

        return AdminResponse.builder()
                .seq(admin.getSeq())
                .email(admin.getEmail())
                .adminName(admin.getAdminName())
                .affiliation(admin.getAffiliation())
                .department(admin.getDepartment())
                .positionTitle(admin.getPositionTitle())
                .phoneNumber(admin.getPhoneNumber())
                .contactEmail(admin.getContactEmail())
                .role(admin.getRole())
                .status(admin.getStatus())
                .reviewerProfile(loadReviewerProfile(conferenceSeq, admin))
                .lastLoginAt(admin.getLastLoginAt())
                .createdAt(admin.getCreatedAt())
                .updatedAt(admin.getUpdatedAt())
                .build();
    }

    /**
     * 모든 활성 관리자 조회
     */
    public List<AdminResponse> getAllActiveAdmins(Long conferenceSeq) {
        List<AdminAccount> admins = adminAccountRepository.findAllActive(dbEncString());
        return admins.stream()
                .map(admin -> toResponse(conferenceSeq, admin))
                .collect(Collectors.toList());
    }

    /**
     * 모든 관리자 조회
     */
    public List<AdminResponse> getAllAdmins(Long conferenceSeq) {
        List<AdminAccount> admins = adminAccountRepository.findAll(dbEncString());
        return admins.stream()
                .map(admin -> toResponse(conferenceSeq, admin))
                .collect(Collectors.toList());
    }

    public AdminPageResponse findPage(Long conferenceSeq, int page, int size, String keyword) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        long totalCount = adminAccountRepository.countByKeyword(conferenceSeq, normalizedKeyword, dbEncString());
        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / safeSize));
        int adjustedPage = Math.min(safePage, totalPages);
        int offset = (adjustedPage - 1) * safeSize;

        List<AdminResponse> items = adminAccountRepository.findPage(
                        conferenceSeq, normalizedKeyword, safeSize, offset, dbEncString()
                ).stream()
                .map(admin -> toResponse(conferenceSeq, admin))
                .toList();

        return AdminPageResponse.builder()
                .items(items)
                .page(adjustedPage)
                .size(safeSize)
                .totalCount(totalCount)
                .adminCount(adminAccountRepository.countByKeywordAndRole(
                        conferenceSeq, normalizedKeyword, "admin", dbEncString()
                ))
                .reviewerCount(adminAccountRepository.countByKeywordAndRole(
                        conferenceSeq, normalizedKeyword, "reviewer", dbEncString()
                ))
                .maintenanceCount(adminAccountRepository.countByKeywordAndRole(
                        conferenceSeq, normalizedKeyword, "maintenance", dbEncString()
                ))
                .totalPages(totalPages)
                .build();
    }

    /**
     * 관리자 정보 업데이트
     */
    @Transactional
    public AdminResponse updateAdmin(
            Long conferenceSeq,
            Long seq,
            String adminName,
            String role,
            String status,
            String affiliation,
            String department,
            String positionTitle,
            String phoneNumber,
            String contactEmail,
            ReviewerProfileRequest reviewerProfileRequest
    ) {
        AdminAccount admin = adminAccountRepository.findBySeq(seq, dbEncString());
        if (admin == null) {
            throw new IllegalArgumentException("존재하지 않는 관리자입니다.");
        }

        if (adminName != null && !adminName.trim().isEmpty()) {
            admin.setAdminName(adminName);
        }

        if (role != null && ("admin".equals(role) || "reviewer".equals(role) || "maintenance".equals(role))) {
            admin.setRole(role);
        }

        if (status != null && ("active".equals(status) || "inactive".equals(status))) {
            admin.setStatus(status);
        }

        applyRequiredContactInfo(
                admin,
                valueOrExisting(affiliation, admin.getAffiliation()),
                valueOrExisting(department, admin.getDepartment()),
                valueOrExisting(positionTitle, admin.getPositionTitle()),
                valueOrExisting(phoneNumber, admin.getPhoneNumber()),
                valueOrExisting(contactEmail, admin.getContactEmail())
        );

        ReviewerProfileResponse reviewerProfile = null;
        if ("reviewer".equals(admin.getRole())) {
            reviewerProfile = saveReviewerProfile(
                    conferenceSeq,
                    seq,
                    reviewerProfileWithContactInfo(admin, reviewerProfileRequest)
            );
        } else {
            reviewerRepository.deleteByAdminSeq(conferenceSeq, seq);
        }

        adminAccountRepository.update(admin, dbEncString());

        return AdminResponse.builder()
                .seq(admin.getSeq())
                .email(admin.getEmail())
                .adminName(admin.getAdminName())
                .affiliation(admin.getAffiliation())
                .department(admin.getDepartment())
                .positionTitle(admin.getPositionTitle())
                .phoneNumber(admin.getPhoneNumber())
                .contactEmail(admin.getContactEmail())
                .role(admin.getRole())
                .status(admin.getStatus())
                .reviewerProfile(reviewerProfile)
                .lastLoginAt(admin.getLastLoginAt())
                .createdAt(admin.getCreatedAt())
                .updatedAt(admin.getUpdatedAt())
                .message("관리자 정보가 업데이트되었습니다.")
                .build();
    }

    /**
     * 관리자 삭제
     */
    @Transactional
    public void deleteAdmin(Long seq) {
        AdminAccount admin = adminAccountRepository.findBySeq(seq, dbEncString());
        if (admin == null) {
            throw new IllegalArgumentException("존재하지 않는 관리자입니다.");
        }

        adminAccountRepository.delete(seq);
    }

    /**
     * 비밀번호 변경
     */
    public void changePassword(Long seq, String oldPassword, String newPassword) {
        AdminAccount admin = adminAccountRepository.findBySeq(seq, dbEncString());
        if (admin == null) {
            throw new IllegalArgumentException("존재하지 않는 관리자입니다.");
        }

        // 기존 비밀번호 검증
        if (!passwordEncoder.matches(oldPassword, admin.getPassword())) {
            throw new IllegalArgumentException("기존 비밀번호가 일치하지 않습니다.");
        }

        // 새 비밀번호 유효성 검사
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("새 비밀번호는 최소 8자 이상이어야 합니다.");
        }

        admin.setPassword(passwordEncoder.encode(newPassword));
        adminAccountRepository.updatePassword(admin);
    }

    public String resetPassword(Long seq) {
        AdminAccount admin = adminAccountRepository.findBySeq(seq, dbEncString());
        if (admin == null) {
            throw new IllegalArgumentException("존재하지 않는 관리자입니다.");
        }

        String newPassword = generatePassword();
        admin.setPassword(passwordEncoder.encode(newPassword));
        adminAccountRepository.resetPasswordAndLoginFailures(admin);
        return newPassword;
    }

    public ExcelExportResult createAdminsXlsx(Long conferenceSeq, String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        List<AdminResponse> admins = adminAccountRepository.findAllForExport(
                        conferenceSeq, normalizedKeyword, dbEncString()
                ).stream()
                .map(admin -> toResponse(conferenceSeq, admin))
                .toList();

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
                addZipEntry(zip, "[Content_Types].xml", contentTypesXml());
                addZipEntry(zip, "_rels/.rels", rootRelationshipsXml());
                addZipEntry(zip, "xl/workbook.xml", workbookXml());
                addZipEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelationshipsXml());
                addZipEntry(zip, "xl/styles.xml", stylesXml());
                addZipEntry(zip, "xl/worksheets/sheet1.xml", worksheetXml(admins));
            }
            return new ExcelExportResult(outputStream.toByteArray(), admins.size(), "admin-accounts.xlsx");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create admins xlsx", e);
        }
    }

    private AdminResponse toResponse(Long conferenceSeq, AdminAccount admin) {
        return AdminResponse.builder()
                .seq(admin.getSeq())
                .email(admin.getEmail())
                .adminName(admin.getAdminName())
                .affiliation(admin.getAffiliation())
                .department(admin.getDepartment())
                .positionTitle(admin.getPositionTitle())
                .phoneNumber(admin.getPhoneNumber())
                .contactEmail(admin.getContactEmail())
                .role(admin.getRole())
                .status(admin.getStatus())
                .reviewerProfile(loadReviewerProfile(conferenceSeq, admin))
                .lastLoginAt(admin.getLastLoginAt())
                .createdAt(admin.getCreatedAt())
                .updatedAt(admin.getUpdatedAt())
                .build();
    }

    public List<AbstractCategoryResponse> getReviewerCategories() {
        return reviewerRepository.findEnabledCategories();
    }

    public ReviewerOwnProfileResponse getOwnReviewerProfile(Long conferenceSeq, Long adminSeq) {
        AdminAccount admin = requireActiveReviewer(adminSeq);
        return toOwnReviewerProfileResponse(admin, loadReviewerProfile(conferenceSeq, admin));
    }

    @Transactional
    public ReviewerOwnProfileResponse updateOwnReviewerProfile(
            Long conferenceSeq,
            Long adminSeq,
            ReviewerOwnProfileUpdateRequest request
    ) {
        AdminAccount admin = requireActiveReviewer(adminSeq);
        if (request == null) {
            throw new IllegalArgumentException("수정할 Reviewer 정보가 없습니다.");
        }

        String adminName = trimToNull(request.getAdminName());
        if (adminName == null) {
            throw new IllegalArgumentException("Reviewer 이름은 필수입니다.");
        }
        validateLength(adminName, 255, "Reviewer 이름");

        List<String> expertiseCodes = request.getExpertiseCodes() == null
                ? null
                : request.getExpertiseCodes().stream().map(String::valueOf).toList();
        ReviewerProfileResponse reviewerProfile = saveReviewerProfile(
                conferenceSeq,
                adminSeq,
                ReviewerProfileRequest.builder()
                        .affiliation(request.getAffiliation())
                        .department(request.getDepartment())
                        .positionTitle(request.getPositionTitle())
                        .phoneNumber(request.getPhoneNumber())
                        .contactEmail(request.getContactEmail())
                        .expertiseCodes(expertiseCodes)
                        .build()
        );

        admin.setAdminName(adminName);
        admin.setAffiliation(reviewerProfile.getAffiliation());
        admin.setDepartment(reviewerProfile.getDepartment());
        admin.setPositionTitle(reviewerProfile.getPositionTitle());
        admin.setPhoneNumber(reviewerProfile.getPhoneNumber());
        admin.setContactEmail(reviewerProfile.getContactEmail());
        adminAccountRepository.updateOwnProfile(admin, dbEncString());
        return toOwnReviewerProfileResponse(admin, reviewerProfile);
    }

    public void changeOwnPassword(Long adminSeq, AdminPasswordChangeRequest request) {
        AdminAccount admin = adminAccountRepository.findBySeq(adminSeq, dbEncString());
        if (admin == null
                || !"active".equals(admin.getStatus())
                || (!("admin".equals(admin.getRole())) && !("reviewer".equals(admin.getRole()))
                && !("maintenance".equals(admin.getRole())))) {
            throw new IllegalArgumentException("사용할 수 없는 관리자 계정입니다.");
        }
        if (request == null || request.getCurrentPassword() == null || request.getCurrentPassword().isEmpty()) {
            throw new IllegalArgumentException("현재 비밀번호는 필수입니다.");
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), admin.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < 8) {
            throw new IllegalArgumentException("새 비밀번호는 최소 8자 이상이어야 합니다.");
        }
        if (!request.getNewPassword().equals(request.getNewPasswordConfirm())) {
            throw new IllegalArgumentException("새 비밀번호와 비밀번호 확인이 일치하지 않습니다.");
        }
        if (passwordEncoder.matches(request.getNewPassword(), admin.getPassword())) {
            throw new IllegalArgumentException("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }

        admin.setPassword(passwordEncoder.encode(request.getNewPassword()));
        adminAccountRepository.updatePassword(admin);
    }

    private AdminAccount requireActiveReviewer(Long adminSeq) {
        AdminAccount admin = adminAccountRepository.findBySeq(adminSeq, dbEncString());
        if (admin == null || !"active".equals(admin.getStatus()) || !"reviewer".equals(admin.getRole())) {
            throw new IllegalArgumentException("사용할 수 없는 Reviewer 계정입니다.");
        }
        return admin;
    }

    private ReviewerOwnProfileResponse toOwnReviewerProfileResponse(
            AdminAccount admin,
            ReviewerProfileResponse reviewerProfile
    ) {
        return ReviewerOwnProfileResponse.builder()
                .seq(admin.getSeq())
                .email(admin.getEmail())
                .adminName(admin.getAdminName())
                .role(admin.getRole())
                .status(admin.getStatus())
                .reviewerProfile(reviewerProfile)
                .build();
    }

    private ReviewerProfileResponse saveReviewerProfile(
            Long conferenceSeq,
            Long adminSeq,
            ReviewerProfileRequest request
    ) {
        ReviewerProfile existing = reviewerRepository.findByAdminSeq(
                conferenceSeq, adminSeq, dbEncString()
        );
        ReviewerProfileRequest safeRequest = request == null ? ReviewerProfileRequest.builder().build() : request;

        String affiliation = valueOrExisting(safeRequest.getAffiliation(), existing == null ? null : existing.getAffiliation());
        String department = valueOrExisting(safeRequest.getDepartment(), existing == null ? null : existing.getDepartment());
        String positionTitle = valueOrExisting(safeRequest.getPositionTitle(), existing == null ? null : existing.getPositionTitle());
        String phoneNumber = valueOrExisting(safeRequest.getPhoneNumber(), existing == null ? null : existing.getPhoneNumber());
        String contactEmail = valueOrExisting(safeRequest.getContactEmail(), existing == null ? null : existing.getContactEmail());
        String isUsed = normalizeIsUsed(safeRequest.getIsUsed(), existing == null ? null : existing.getIsUsed());

        if (affiliation == null) {
            throw new IllegalArgumentException("Reviewer 소속기관은 필수입니다.");
        }
        if (department == null) {
            throw new IllegalArgumentException("Reviewer 부서·학과·진료과는 필수입니다.");
        }
        if (positionTitle == null) {
            throw new IllegalArgumentException("Reviewer 직위는 필수입니다.");
        }
        if (phoneNumber == null) {
            throw new IllegalArgumentException("Reviewer 연락처는 필수입니다.");
        }
        if (contactEmail == null) {
            throw new IllegalArgumentException("Reviewer 이메일은 필수입니다.");
        }
        validateLength(affiliation, 255, "Reviewer 소속기관");
        validateLength(department, 255, "Reviewer 부서·학과·진료과");
        validateLength(positionTitle, 100, "Reviewer 직위");
        validateLength(phoneNumber, 100, "Reviewer 연락처");
        validateLength(contactEmail, 255, "Reviewer 이메일");
        if (!contactEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Reviewer 이메일 형식이 올바르지 않습니다.");
        }

        ReviewerProfile reviewer = ReviewerProfile.builder()
                .seq(existing == null ? null : existing.getSeq())
                .conferenceSeq(conferenceSeq)
                .adminSeq(adminSeq)
                .affiliation(affiliation)
                .department(department)
                .positionTitle(positionTitle)
                .phoneNumber(phoneNumber)
                .contactEmail(contactEmail)
                .isUsed(isUsed)
                .isDelete("N")
                .build();

        if (existing == null) {
            reviewerRepository.insert(conferenceSeq, reviewer, dbEncString());
        } else {
            reviewerRepository.update(conferenceSeq, reviewer, dbEncString());
        }

        List<Long> expertiseCodes = safeRequest.getExpertiseCodes() == null
                ? (existing == null ? List.of() : reviewerRepository.findExpertiseCodes(reviewer.getSeq()))
                : normalizeExpertiseCodes(safeRequest.getExpertiseCodes());

        if (safeRequest.getExpertiseCodes() != null) {
            for (Long categoryCode : expertiseCodes) {
                if (reviewerRepository.countEnabledCategory(categoryCode) == 0) {
                    throw new IllegalArgumentException("사용할 수 없는 Reviewer 전문분야가 포함되어 있습니다.");
                }
            }
            reviewerRepository.deleteExpertise(reviewer.getSeq());
            if (!expertiseCodes.isEmpty()) {
                reviewerRepository.insertExpertise(reviewer.getSeq(), expertiseCodes);
            }
        }

        return toReviewerProfileResponse(reviewer, expertiseCodes);
    }

    private ReviewerProfileResponse loadReviewerProfile(Long conferenceSeq, AdminAccount admin) {
        if (!"reviewer".equals(admin.getRole())) {
            return null;
        }
        ReviewerProfile reviewer = reviewerRepository.findByAdminSeq(
                conferenceSeq, admin.getSeq(), dbEncString()
        );
        if (reviewer == null) {
            return null;
        }
        return toReviewerProfileResponse(reviewer, reviewerRepository.findExpertiseCodes(reviewer.getSeq()));
    }

    private ReviewerProfileResponse toReviewerProfileResponse(ReviewerProfile reviewer, List<Long> expertiseCodes) {
        return ReviewerProfileResponse.builder()
                .seq(reviewer.getSeq())
                .affiliation(reviewer.getAffiliation())
                .department(reviewer.getDepartment())
                .positionTitle(reviewer.getPositionTitle())
                .phoneNumber(reviewer.getPhoneNumber())
                .contactEmail(reviewer.getContactEmail())
                .isUsed(reviewer.getIsUsed())
                .expertiseCodes(expertiseCodes)
                .build();
    }

    private ReviewerProfileRequest reviewerProfileWithContactInfo(
            AdminAccount admin,
            ReviewerProfileRequest request
    ) {
        ReviewerProfileRequest safeRequest = request == null ? ReviewerProfileRequest.builder().build() : request;
        return ReviewerProfileRequest.builder()
                .affiliation(admin.getAffiliation())
                .department(admin.getDepartment())
                .positionTitle(admin.getPositionTitle())
                .phoneNumber(admin.getPhoneNumber())
                .contactEmail(admin.getContactEmail())
                .isUsed(safeRequest.getIsUsed())
                .expertiseCodes(safeRequest.getExpertiseCodes())
                .build();
    }

    private void applyRequiredContactInfo(
            AdminAccount admin,
            String affiliation,
            String department,
            String positionTitle,
            String phoneNumber,
            String contactEmail
    ) {
        if (affiliation == null) {
            throw new IllegalArgumentException("소속기관은 필수입니다.");
        }
        if (department == null) {
            throw new IllegalArgumentException("부서·학과·진료과는 필수입니다.");
        }
        if (positionTitle == null) {
            throw new IllegalArgumentException("직위는 필수입니다.");
        }
        if (phoneNumber == null) {
            throw new IllegalArgumentException("연락처는 필수입니다.");
        }
        if (contactEmail == null) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
        validateLength(affiliation, 255, "소속기관");
        validateLength(department, 255, "부서·학과·진료과");
        validateLength(positionTitle, 100, "직위");
        validateLength(phoneNumber, 100, "연락처");
        validateLength(contactEmail, 255, "이메일");
        if (!contactEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
        }

        admin.setAffiliation(affiliation);
        admin.setDepartment(department);
        admin.setPositionTitle(positionTitle);
        admin.setPhoneNumber(phoneNumber);
        admin.setContactEmail(contactEmail);
    }

    private List<Long> normalizeExpertiseCodes(List<String> expertiseCodes) {
        Set<Long> normalized = new LinkedHashSet<>();
        for (String categoryCode : expertiseCodes) {
            String value = trimToNull(categoryCode);
            if (value != null) {
                try {
                    long parsed = Long.parseLong(value);
                    if (parsed < 1) {
                        throw new NumberFormatException();
                    }
                    normalized.add(parsed);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Reviewer 전문분야 코드가 올바르지 않습니다.");
                }
            }
        }
        return List.copyOf(normalized);
    }

    private String valueOrExisting(String value, String existingValue) {
        return value == null ? trimToNull(existingValue) : trimToNull(value);
    }

    private String normalizeIsUsed(String value, String existingValue) {
        String normalized = value == null ? trimToNull(existingValue) : value.trim().toUpperCase();
        if (normalized == null) {
            return "Y";
        }
        if (!"Y".equals(normalized) && !"N".equals(normalized)) {
            throw new IllegalArgumentException("Reviewer 사용 여부는 Y 또는 N이어야 합니다.");
        }
        return normalized;
    }

    private void validateLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "은(는) " + maxLength + "자 이하로 입력해야 합니다.");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void addZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String contentTypesXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                </Types>
                """;
    }

    private String rootRelationshipsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
                """;
    }

    private String workbookXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="Admins" sheetId="1" r:id="rId1"/>
                  </sheets>
                </workbook>
                """;
    }

    private String workbookRelationshipsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>
                """;
    }

    private String stylesXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <fonts count="2">
                    <font><sz val="11"/><name val="Calibri"/></font>
                    <font><b/><sz val="11"/><name val="Calibri"/></font>
                  </fonts>
                  <fills count="2">
                    <fill><patternFill patternType="none"/></fill>
                    <fill><patternFill patternType="gray125"/></fill>
                  </fills>
                  <borders count="1"><border/></borders>
                  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                  <cellXfs count="2">
                    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
                  </cellXfs>
                </styleSheet>
                """;
    }

    private String worksheetXml(List<AdminResponse> admins) {
        String[] headers = {
                "관리자번호", "관리자 아이디", "관리자명", "소속기관", "부서·학과·진료과",
                "직위", "연락처", "이메일", "권한", "상태", "마지막 로그인", "생성일", "수정일"
        };
        StringBuilder rows = new StringBuilder();
        rows.append("<row r=\"1\">");
        for (int columnIndex = 0; columnIndex < headers.length; columnIndex++) {
            rows.append(cell(columnIndex, 1, headers[columnIndex], 1));
        }
        rows.append("</row>");

        for (int index = 0; index < admins.size(); index++) {
            AdminResponse admin = admins.get(index);
            int rowNumber = index + 2;
            rows.append("<row r=\"").append(rowNumber).append("\">")
                    .append(cell(0, rowNumber, admin.getSeq(), 0))
                    .append(cell(1, rowNumber, admin.getEmail(), 0))
                    .append(cell(2, rowNumber, admin.getAdminName(), 0))
                    .append(cell(3, rowNumber, admin.getAffiliation(), 0))
                    .append(cell(4, rowNumber, admin.getDepartment(), 0))
                    .append(cell(5, rowNumber, admin.getPositionTitle(), 0))
                    .append(cell(6, rowNumber, admin.getPhoneNumber(), 0))
                    .append(cell(7, rowNumber, admin.getContactEmail(), 0))
                    .append(cell(8, rowNumber, admin.getRole(), 0))
                    .append(cell(9, rowNumber, admin.getStatus(), 0))
                    .append(cell(10, rowNumber, admin.getLastLoginAt(), 0))
                    .append(cell(11, rowNumber, admin.getCreatedAt(), 0))
                    .append(cell(12, rowNumber, admin.getUpdatedAt(), 0))
                    .append("</row>");
        }

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <cols>
                    <col min="1" max="1" width="12" customWidth="1"/>
                    <col min="2" max="2" width="34" customWidth="1"/>
                    <col min="3" max="3" width="24" customWidth="1"/>
                    <col min="4" max="5" width="26" customWidth="1"/>
                    <col min="6" max="6" width="18" customWidth="1"/>
                    <col min="7" max="8" width="28" customWidth="1"/>
                    <col min="9" max="10" width="12" customWidth="1"/>
                    <col min="11" max="13" width="24" customWidth="1"/>
                  </cols>
                  <sheetData>
                %s
                  </sheetData>
                </worksheet>
                """.formatted(rows);
    }

    private String cell(int columnIndex, int rowNumber, Object value, int styleIndex) {
        return "<c r=\"" + cellReference(columnIndex, rowNumber) + "\" t=\"inlineStr\" s=\"" + styleIndex + "\"><is><t>" +
                xmlValue(value) +
                "</t></is></c>";
    }

    private String cellReference(int columnIndex, int rowNumber) {
        StringBuilder columnName = new StringBuilder();
        int value = columnIndex;

        do {
            columnName.insert(0, (char) ('A' + (value % 26)));
            value = value / 26 - 1;
        } while (value >= 0);

        return columnName + String.valueOf(rowNumber);
    }

    private String xmlValue(Object value) {
        if (value == null) {
            return "";
        }

        String text = String.valueOf(value);
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String dbEncString() {
        return personalDataProperties.requireDbEncString();
    }

    private String generatePassword() {
        char[] password = new char[8];
        password[0] = PASSWORD_LETTERS[SECURE_RANDOM.nextInt(PASSWORD_LETTERS.length)];
        password[1] = PASSWORD_DIGITS[SECURE_RANDOM.nextInt(PASSWORD_DIGITS.length)];
        password[2] = PASSWORD_SPECIALS[SECURE_RANDOM.nextInt(PASSWORD_SPECIALS.length)];
        password[3] = PASSWORD_SPECIALS[SECURE_RANDOM.nextInt(PASSWORD_SPECIALS.length)];

        for (int index = 4; index < password.length; index++) {
            password[index] = PASSWORD_ALPHANUMERIC[SECURE_RANDOM.nextInt(PASSWORD_ALPHANUMERIC.length)];
        }

        for (int index = password.length - 1; index > 0; index--) {
            int swapIndex = SECURE_RANDOM.nextInt(index + 1);
            char temp = password[index];
            password[index] = password[swapIndex];
            password[swapIndex] = temp;
        }

        return new String(password);
    }
}

