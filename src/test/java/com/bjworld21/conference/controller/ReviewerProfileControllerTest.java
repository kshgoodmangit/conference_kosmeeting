package com.bjworld21.conference.controller;

import com.bjworld21.conference.dto.ReviewerOwnProfileResponse;
import com.bjworld21.conference.dto.ReviewerOwnProfileUpdateRequest;
import com.bjworld21.conference.service.AdminService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewerProfileControllerTest {

    @Test
    void updateUsesOnlyLoggedInAdminSequence() {
        AdminService adminService = mock(AdminService.class);
        ReviewerProfileController controller = new ReviewerProfileController(adminService);
        ReviewerOwnProfileUpdateRequest request = ReviewerOwnProfileUpdateRequest.builder()
                .adminName("Reviewer")
                .affiliation("Hospital")
                .department("Cardiology")
                .build();
        ReviewerOwnProfileResponse response = ReviewerOwnProfileResponse.builder()
                .seq(42L)
                .adminName("Reviewer")
                .role("reviewer")
                .status("active")
                .build();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("adminSeq", 42L);
        session.setAttribute("reviewerConferenceSeq", 1L);
        when(adminService.updateOwnReviewerProfile(1L, 42L, request)).thenReturn(response);

        var result = controller.updateOwnProfile(request, session);

        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getBody()).isEqualTo(response);
        verify(adminService).updateOwnReviewerProfile(1L, 42L, request);
    }
}
