package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.dto.AdminCreateRequest;
import com.bjworld21.conference.dto.AdminLoginRequest;
import com.bjworld21.conference.dto.AdminLoginResponse;
import com.bjworld21.conference.dto.AdminPasswordChangeRequest;
import com.bjworld21.conference.dto.AdminResponse;
import com.bjworld21.conference.dto.AbstractCategoryResponse;
import com.bjworld21.conference.dto.ReviewerOwnProfileResponse;
import com.bjworld21.conference.dto.ReviewerOwnProfileUpdateRequest;
import com.bjworld21.conference.dto.ReviewerProfileRequest;
import com.bjworld21.conference.dto.TestReviewerCreateResponse;
import com.bjworld21.conference.entity.AdminAccount;
import com.bjworld21.conference.entity.ReviewerProfile;
import com.bjworld21.conference.repository.AdminAccountRepository;
import com.bjworld21.conference.repository.ReviewerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AdminAccountRepository adminAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ReviewerRepository reviewerRepository;

    @Mock
    private PersonalDataProperties personalDataProperties;

    @Mock
    private AdminCredentialVerifier adminCredentialVerifier;

    @InjectMocks
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        lenient().when(personalDataProperties.requireDbEncString())
                .thenReturn(PersonalDataTestSupport.DB_ENC_STRING);
    }

    @Test
    void createsReviewerAccount() {
        AdminCreateRequest request = AdminCreateRequest.builder()
                .email("reviewer@example.com")
                .password("password123")
                .adminName("Reviewer")
                .affiliation("ICMS Hospital")
                .department("Cardiology")
                .positionTitle("Professor")
                .phoneNumber("010-1234-5678")
                .contactEmail("reviewer-contact@example.com")
                .role("reviewer")
                .reviewerProfile(ReviewerProfileRequest.builder()
                        .isUsed("Y")
                        .build())
                .build();

        when(adminAccountRepository.findByEmail(request.getEmail(), PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(null);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");

        AdminResponse response = adminService.create(1L, request);

        assertThat(response.getRole()).isEqualTo("reviewer");
        assertThat(response.getAffiliation()).isEqualTo("ICMS Hospital");
        assertThat(response.getReviewerProfile().getAffiliation()).isEqualTo("ICMS Hospital");
        verify(adminAccountRepository).insert(any(AdminAccount.class), eq(PersonalDataTestSupport.DB_ENC_STRING));
    }

    @Test
    void createsMaintenanceAccount() {
        AdminCreateRequest request = AdminCreateRequest.builder()
                .email("maintenance@example.com")
                .password("password123")
                .adminName("Maintenance")
                .affiliation("ICMS")
                .department("Operations")
                .positionTitle("Engineer")
                .phoneNumber("010-1234-5678")
                .contactEmail("maintenance@example.com")
                .role("maintenance")
                .build();

        when(adminAccountRepository.findByEmail(
                request.getEmail(), PersonalDataTestSupport.DB_ENC_STRING
        )).thenReturn(null);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");

        AdminResponse response = adminService.create(1L, request);

        assertThat(response.getRole()).isEqualTo("maintenance");
        verify(adminAccountRepository).insert(any(AdminAccount.class), eq(PersonalDataTestSupport.DB_ENC_STRING));
        verify(reviewerRepository, never()).insert(
                eq(1L), any(ReviewerProfile.class), eq(PersonalDataTestSupport.DB_ENC_STRING)
        );
    }

    @Test
    void requiresAllAdminBasicInformation() {
        List<ReviewerProfileRequest> invalidProfiles = List.of(
                reviewerProfile(null, "Cardiology", "Professor", "010-1234-5678", "reviewer@example.com"),
                reviewerProfile("ICMS Hospital", null, "Professor", "010-1234-5678", "reviewer@example.com"),
                reviewerProfile("ICMS Hospital", "Cardiology", null, "010-1234-5678", "reviewer@example.com"),
                reviewerProfile("ICMS Hospital", "Cardiology", "Professor", null, "reviewer@example.com"),
                reviewerProfile("ICMS Hospital", "Cardiology", "Professor", "010-1234-5678", null)
        );
        List<String> expectedMessages = List.of(
                "소속기관은 필수입니다.",
                "부서·학과·진료과는 필수입니다.",
                "직위는 필수입니다.",
                "연락처는 필수입니다.",
                "이메일은 필수입니다."
        );

        for (int index = 0; index < invalidProfiles.size(); index++) {
            ReviewerProfileRequest profile = invalidProfiles.get(index);
            String expectedMessage = expectedMessages.get(index);
            AdminCreateRequest request = AdminCreateRequest.builder()
                    .email("reviewer" + index)
                    .password("password123")
                    .adminName("Administrator")
                    .affiliation(profile.getAffiliation())
                    .department(profile.getDepartment())
                    .positionTitle(profile.getPositionTitle())
                    .phoneNumber(profile.getPhoneNumber())
                    .contactEmail(profile.getContactEmail())
                    .role("admin")
                    .build();

            assertThatThrownBy(() -> adminService.create(1L, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(expectedMessage);
        }
    }

    @Test
    void reviewerLoginUsesConferenceAssignedToCurrentReviewerProfile() {
        AdminAccount account = AdminAccount.builder()
                .seq(42L)
                .email("reviewer@example.com")
                .password("encoded-password")
                .adminName("Reviewer")
                .role("reviewer")
                .status("active")
                .build();
        when(adminCredentialVerifier.verifyLogin("reviewer@example.com", "password")).thenReturn(account);
        when(reviewerRepository.findCurrentConferenceSeqByAdminSeq(42L)).thenReturn(7L);

        AdminLoginResponse response = adminService.login(AdminLoginRequest.builder()
                .email("reviewer@example.com")
                .password("password")
                .build());

        assertThat(response.getConferenceSeq()).isEqualTo(7L);
        verify(adminAccountRepository).updateLastLoginAt(42L);
    }

    @Test
    void rejectsReviewerLoginWhenReviewerBelongsOnlyToClosedConference() {
        AdminAccount account = AdminAccount.builder()
                .seq(42L)
                .email("reviewer@example.com")
                .password("encoded-password")
                .adminName("Reviewer")
                .role("reviewer")
                .status("active")
                .build();
        when(adminCredentialVerifier.verifyLogin("reviewer@example.com", "password")).thenReturn(account);
        when(reviewerRepository.findCurrentConferenceSeqByAdminSeq(42L)).thenReturn(null);
        when(reviewerRepository.countActiveByAdminSeq(42L)).thenReturn(1L);

        assertThatThrownBy(() -> adminService.login(AdminLoginRequest.builder()
                .email("reviewer@example.com")
                .password("password")
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("종료된 학술대회의 심사자 계정입니다. 현재 진행 중인 학술대회 심사자만 로그인할 수 있습니다.");

        verify(adminAccountRepository, never()).updateLastLoginAt(42L);
    }

    @Test
    void updatesAccountToReviewerRole() {
        AdminAccount account = AdminAccount.builder()
                .seq(1L)
                .email("operator@example.com")
                .password("encoded-password")
                .adminName("Operator")
                .affiliation("Old Hospital")
                .department("Old Department")
                .positionTitle("Manager")
                .phoneNumber("010-0000-0000")
                .contactEmail("operator@example.com")
                .role("admin")
                .status("active")
                .build();
        when(adminAccountRepository.findBySeq(1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(account);

        ReviewerProfileRequest reviewerProfile = ReviewerProfileRequest.builder()
                .affiliation("ICMS Hospital")
                .department("Cardiology")
                .positionTitle("Professor")
                .phoneNumber("010-1234-5678")
                .contactEmail("reviewer-contact@example.com")
                .isUsed("Y")
                .build();

        AdminResponse response = adminService.updateAdmin(
                1L, 1L, null, "reviewer", null,
                "ICMS Hospital", "Cardiology", "Professor", "010-1234-5678",
                "reviewer-contact@example.com", reviewerProfile
        );

        assertThat(response.getRole()).isEqualTo("reviewer");
        assertThat(account.getRole()).isEqualTo("reviewer");
        verify(adminAccountRepository).update(account, PersonalDataTestSupport.DB_ENC_STRING);
    }

    @Test
    void checksAdminIdAvailability() {
        when(adminAccountRepository.findByEmail("new-admin", PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(null);
        when(adminAccountRepository.findByEmail("existing-admin", PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(AdminAccount.builder().seq(1L).build());

        assertThat(adminService.isAdminIdAvailable(" new-admin ")).isTrue();
        assertThat(adminService.isAdminIdAvailable("existing-admin")).isFalse();
    }

    @Test
    void resetsPasswordWithEightCharactersAndExactlyTwoSpecialCharacters() {
        AdminAccount account = AdminAccount.builder()
                .seq(1L)
                .email("admin")
                .build();
        when(adminAccountRepository.findBySeq(1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(account);
        when(passwordEncoder.encode(any(String.class))).thenReturn("encoded-password");

        String password = adminService.resetPassword(1L);

        assertThat(password).hasSize(8);
        assertThat(password.chars().filter(Character::isLetter).count()).isGreaterThanOrEqualTo(1);
        assertThat(password.chars().filter(Character::isDigit).count()).isGreaterThanOrEqualTo(1);
        assertThat(password.chars().filter(character -> "!@#$%^&*".indexOf(character) >= 0).count()).isEqualTo(2);
        verify(passwordEncoder).encode(password);
        verify(adminAccountRepository).resetPasswordAndLoginFailures(account);
    }

    @Test
    void reviewerUpdatesOnlyOwnEditableProfileFields() {
        AdminAccount account = AdminAccount.builder()
                .seq(1L)
                .email("reviewer@example.com")
                .adminName("Old Name")
                .role("reviewer")
                .status("active")
                .build();
        ReviewerProfile profile = ReviewerProfile.builder()
                .seq(11L)
                .adminSeq(1L)
                .affiliation("Old Hospital")
                .department("Old Department")
                .positionTitle("Professor")
                .phoneNumber("010-0000-0000")
                .contactEmail("old-reviewer@example.com")
                .isUsed("Y")
                .isDelete("N")
                .build();
        when(adminAccountRepository.findBySeq(1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(account);
        when(reviewerRepository.findByAdminSeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(profile);
        when(reviewerRepository.countEnabledCategory(2L)).thenReturn(1L);

        ReviewerOwnProfileResponse response = adminService.updateOwnReviewerProfile(
                1L,
                1L,
                ReviewerOwnProfileUpdateRequest.builder()
                        .adminName("New Name")
                        .affiliation("New Hospital")
                        .department("Cardiology")
                        .positionTitle("Associate Professor")
                        .phoneNumber("010-1234-5678")
                        .contactEmail("reviewer-contact@example.com")
                        .expertiseCodes(List.of(2L))
                        .build()
        );

        assertThat(response.getAdminName()).isEqualTo("New Name");
        assertThat(response.getReviewerProfile().getAffiliation()).isEqualTo("New Hospital");
        assertThat(response.getReviewerProfile().getIsUsed()).isEqualTo("Y");
        assertThat(account.getRole()).isEqualTo("reviewer");
        assertThat(account.getStatus()).isEqualTo("active");
        assertThat(account.getContactEmail()).isEqualTo("reviewer-contact@example.com");
        verify(adminAccountRepository).updateOwnProfile(
                account, PersonalDataTestSupport.DB_ENC_STRING
        );
        verify(reviewerRepository).update(
                eq(1L), any(ReviewerProfile.class), eq(PersonalDataTestSupport.DB_ENC_STRING)
        );
        verify(reviewerRepository).insertExpertise(11L, List.of(2L));
    }

    @Test
    void changesOwnPasswordAfterVerifyingCurrentPassword() {
        AdminAccount account = AdminAccount.builder()
                .seq(1L)
                .password("encoded-current")
                .role("reviewer")
                .status("active")
                .build();
        when(adminAccountRepository.findBySeq(1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(account);
        when(passwordEncoder.matches("current-password", "encoded-current")).thenReturn(true);
        when(passwordEncoder.matches("new-password", "encoded-current")).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new");

        adminService.changeOwnPassword(1L, AdminPasswordChangeRequest.builder()
                .currentPassword("current-password")
                .newPassword("new-password")
                .newPasswordConfirm("new-password")
                .build());

        assertThat(account.getPassword()).isEqualTo("encoded-new");
        verify(adminAccountRepository).updatePassword(account);
    }

    @Test
    void rejectsOwnPasswordChangeWhenConfirmationDiffers() {
        AdminAccount account = AdminAccount.builder()
                .seq(1L)
                .password("encoded-current")
                .role("reviewer")
                .status("active")
                .build();
        when(adminAccountRepository.findBySeq(1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(account);
        when(passwordEncoder.matches("current-password", "encoded-current")).thenReturn(true);

        assertThatThrownBy(() -> adminService.changeOwnPassword(1L, AdminPasswordChangeRequest.builder()
                .currentPassword("current-password")
                .newPassword("new-password")
                .newPasswordConfirm("different-password")
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("새 비밀번호와 비밀번호 확인이 일치하지 않습니다.");
    }

    private ReviewerProfileRequest reviewerProfile(
            String affiliation,
            String department,
            String positionTitle,
            String phoneNumber,
            String contactEmail
    ) {
        return ReviewerProfileRequest.builder()
                .affiliation(affiliation)
                .department(department)
                .positionTitle(positionTitle)
                .phoneNumber(phoneNumber)
                .contactEmail(contactEmail)
                .isUsed("Y")
                .build();
    }

    @Test
    void createsTenReviewerTestAccountsWithFixedPassword() {
        when(reviewerRepository.findEnabledCategories()).thenReturn(List.of(
                AbstractCategoryResponse.builder().code(2L).name("임상연구").build()
        ));
        when(reviewerRepository.countEnabledCategory(2L)).thenReturn(1L);
        when(passwordEncoder.encode("reviewer12#$")).thenReturn("encoded-password");

        TestReviewerCreateResponse response = adminService.createTestReviewerAccounts(1L);

        assertThat(response.getCreatedCount()).isEqualTo(10);
        assertThat(response.getSkippedCount()).isZero();
        assertThat(response.getCreatedAccounts()).containsExactly(
                "reviewer1", "reviewer2", "reviewer3", "reviewer4", "reviewer5",
                "reviewer6", "reviewer7", "reviewer8", "reviewer9", "reviewer10"
        );
        verify(passwordEncoder, times(10)).encode("reviewer12#$");
        verify(adminAccountRepository, times(10)).insert(
                any(AdminAccount.class), eq(PersonalDataTestSupport.DB_ENC_STRING)
        );
    }
}
