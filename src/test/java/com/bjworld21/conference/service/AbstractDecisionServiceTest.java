package com.bjworld21.conference.service;

import com.bjworld21.conference.dto.AbstractDecisionRequest;
import com.bjworld21.conference.dto.AbstractSubmissionResponse;
import com.bjworld21.conference.repository.AbstractReviewAssignmentRepository;
import com.bjworld21.conference.repository.AbstractSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractDecisionServiceTest {
    @Mock
    private AbstractSubmissionRepository submissionRepository;
    @Mock
    private AbstractReviewAssignmentRepository assignmentRepository;

    private AbstractDecisionService service;

    @BeforeEach
    void setUp() {
        service = new AbstractDecisionService(
                submissionRepository, assignmentRepository, PersonalDataTestSupport.properties()
        );
        when(submissionRepository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(AbstractSubmissionResponse.builder()
                .seq(1L)
                .status("under_review")
                .build());
    }

    @Test
    void savesNormalApprovalAfterAtLeastTwoReviewersCompletedAllReviews() {
        AbstractDecisionRequest request = request("approved", 10L, null);
        when(assignmentRepository.countEffectiveAssignments(1L, 1L)).thenReturn(2L);
        when(assignmentRepository.countCompletedSubmittedReviews(1L, 1L)).thenReturn(2L);
        when(submissionRepository.countEnabledPresentationTypeByCode(10L)).thenReturn(1L);
        when(submissionRepository.updateDecision(1L, 1L, "approved", 10L, 9L, null, false)).thenReturn(1);

        service.decide(1L, 1L, 9L, request);

        verify(submissionRepository).updateDecision(1L, 1L, "approved", 10L, 9L, null, false);
    }

    @Test
    void requiresReasonWhenReviewIsIncomplete() {
        AbstractDecisionRequest request = request("rejected", null, " ");
        when(assignmentRepository.countEffectiveAssignments(1L, 1L)).thenReturn(2L);
        when(assignmentRepository.countCompletedSubmittedReviews(1L, 1L)).thenReturn(1L);

        assertThatThrownBy(() -> service.decide(1L, 1L, 9L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("강제 결정");
    }

    @Test
    void recordsForcedDecisionWhenFewerThanTwoReviewersExist() {
        AbstractDecisionRequest request = request("rejected", null, "위원회 긴급 결정");
        when(assignmentRepository.countEffectiveAssignments(1L, 1L)).thenReturn(1L);
        when(assignmentRepository.countCompletedSubmittedReviews(1L, 1L)).thenReturn(1L);
        when(submissionRepository.updateDecision(1L, 1L, "rejected", null, 9L, "위원회 긴급 결정", true)).thenReturn(1);

        service.decide(1L, 1L, 9L, request);

        verify(submissionRepository).updateDecision(1L, 1L, "rejected", null, 9L, "위원회 긴급 결정", true);
    }

    private AbstractDecisionRequest request(String decision, Long presentationTypeCode, String reason) {
        AbstractDecisionRequest request = new AbstractDecisionRequest();
        request.setDecision(decision);
        request.setAcceptedPresentationTypeCode(presentationTypeCode);
        request.setReason(reason);
        return request;
    }
}
