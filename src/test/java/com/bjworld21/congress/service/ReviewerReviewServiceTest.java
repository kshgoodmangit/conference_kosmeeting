package com.bjworld21.congress.service;

import com.bjworld21.congress.config.AbstractReviewProperties;
import com.bjworld21.congress.dto.AbstractSubmissionAuthor;
import com.bjworld21.congress.dto.AbstractSubmissionInstitution;
import com.bjworld21.congress.dto.AbstractSubmissionResponse;
import com.bjworld21.congress.dto.ReviewerReviewDetailResponse;
import com.bjworld21.congress.entity.AbstractReviewAssignment;
import com.bjworld21.congress.entity.ReviewerProfile;
import com.bjworld21.congress.repository.AbstractSubmissionRepository;
import com.bjworld21.congress.repository.ReviewerRepository;
import com.bjworld21.congress.repository.ReviewerReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewerReviewServiceTest {
    @Mock
    private ReviewerRepository reviewerRepository;
    @Mock
    private ReviewerReviewRepository reviewerReviewRepository;
    @Mock
    private AbstractSubmissionRepository abstractSubmissionRepository;

    private AbstractReviewProperties properties;
    private ReviewerReviewService service;

    @BeforeEach
    void setUp() {
        properties = new AbstractReviewProperties();
        service = new ReviewerReviewService(
                reviewerRepository, reviewerReviewRepository, abstractSubmissionRepository, properties,
                PersonalDataTestSupport.properties()
        );
        when(reviewerRepository.findByAdminSeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING))
                .thenReturn(ReviewerProfile.builder().seq(10L).adminSeq(1L).build());
        when(reviewerReviewRepository.findAssignmentForReviewer(1L, 100L, 10L))
                .thenReturn(AbstractReviewAssignment.builder()
                        .seq(100L)
                        .abstractSeq(200L)
                        .reviewerSeq(10L)
                        .status("assigned")
                        .build());
        when(abstractSubmissionRepository.findBySeq(1L, 200L, PersonalDataTestSupport.DB_ENC_STRING))
                .thenReturn(abstractWithAuthorInformation());
        when(reviewerReviewRepository.findEvaluationItems(1L, null)).thenReturn(List.of());
    }

    @Test
    void hidesAuthorInformationByDefault() {
        ReviewerReviewDetailResponse response = service.getDetail(1L, 1L, 100L);

        assertThat(response.getShowAuthorInformation()).isFalse();
        assertThat(response.getAbstractSubmission().getMemberSeq()).isNull();
        assertThat(response.getAbstractSubmission().getMemberEmail()).isNull();
        assertThat(response.getAbstractSubmission().getMemberFullName()).isNull();
        assertThat(response.getAbstractSubmission().getMainAuthorName()).isNull();
        assertThat(response.getAbstractSubmission().getAuthors()).isEmpty();
        assertThat(response.getAbstractSubmission().getInstitutions()).isEmpty();
        verify(abstractSubmissionRepository, never()).findAuthorsByAbstractSeq(
                200L, PersonalDataTestSupport.DB_ENC_STRING
        );
        verify(abstractSubmissionRepository, never()).findInstitutionsByAbstractSeq(200L);
    }

    @Test
    void includesAuthorInformationWhenConfigured() {
        properties.setShowAuthorInformation(true);
        when(abstractSubmissionRepository.findAuthorsByAbstractSeq(200L, PersonalDataTestSupport.DB_ENC_STRING))
                .thenReturn(List.of(AbstractSubmissionAuthor.builder().authorName("Author").build()));
        when(abstractSubmissionRepository.findInstitutionsByAbstractSeq(200L))
                .thenReturn(List.of(AbstractSubmissionInstitution.builder().institutionName("Institution").build()));

        ReviewerReviewDetailResponse response = service.getDetail(1L, 1L, 100L);

        assertThat(response.getShowAuthorInformation()).isTrue();
        assertThat(response.getAbstractSubmission().getMemberEmail()).isEqualTo("member@example.com");
        assertThat(response.getAbstractSubmission().getAuthors()).extracting(AbstractSubmissionAuthor::getAuthorName)
                .containsExactly("Author");
        assertThat(response.getAbstractSubmission().getInstitutions())
                .extracting(AbstractSubmissionInstitution::getInstitutionName)
                .containsExactly("Institution");
    }

    private AbstractSubmissionResponse abstractWithAuthorInformation() {
        return AbstractSubmissionResponse.builder()
                .seq(200L)
                .memberSeq(300L)
                .memberEmail("member@example.com")
                .memberFullName("Member Name")
                .mainAuthorName("Author")
                .title("Abstract")
                .build();
    }
}
