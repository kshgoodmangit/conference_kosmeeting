package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PromotionalMailProperties;
import com.bjworld21.conference.dto.MailCampaignRequest;
import com.bjworld21.conference.dto.MailQueueRequest;
import com.bjworld21.conference.entity.MailCampaign;
import com.bjworld21.conference.entity.MailRecipientCandidate;
import com.bjworld21.conference.repository.MailCampaignRepository;
import com.bjworld21.conference.repository.MailRecipientGroupRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MailCampaignRecipientGroupsTest {
    private MailCampaignRepository repository;
    private MailRecipientGroupRepository groups;
    private MailCampaignService service;

    @BeforeEach
    void setUp() {
        repository = mock(MailCampaignRepository.class);
        when(repository.findJobByIdempotencyKey(anyString())).thenReturn(null);
        groups = mock(MailRecipientGroupRepository.class);
        var books = mock(MailAddressBookService.class);
        var selection = new MailRecipientSelectionService(
                groups, repository, books, PersonalDataTestSupport.properties()
        );
        service = new MailCampaignService(repository, books, new MailHtmlSanitizer(),
                new PromotionalMailProperties(), mock(UploadStorage.class), selection,
                PersonalDataTestSupport.properties());
        when(repository.findBySeq(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(MailCampaign.builder().seq(1L).status("DRAFT")
                .mailType("INFORMATION").htmlContent("<p>Notice</p>").versionNo(0).build());
    }

    @Test
    void savesAndRestoresGroupsAndAllowsRemovingThemOnUpdate() {
        var request = campaignRequest();
        request.setRecipientGroups(List.of("ALL_MEMBERS", "ALL_ACCEPTED"));
        doAnswer(invocation -> { ((MailCampaign) invocation.getArgument(1)).setSeq(1L); return null; })
                .when(repository).insert(eq(1L), any(), eq(PersonalDataTestSupport.DB_ENC_STRING));
        when(repository.findGroupSources(1L)).thenReturn(List.of("ALL_MEMBERS", "ALL_ACCEPTED"));

        var detail = service.create(1L, request);

        verify(repository).insertGroupSource(1L, "ALL_MEMBERS");
        verify(repository).insertGroupSource(1L, "ALL_ACCEPTED");
        assertThat(detail.getRecipientGroups()).containsExactly("ALL_MEMBERS", "ALL_ACCEPTED");
        assertThat(detail.getDirectRecipients()).isEmpty();

        request.setRecipientGroups(List.of());
        when(repository.update(eq(1L), any(), eq(PersonalDataTestSupport.DB_ENC_STRING))).thenReturn(1);
        when(repository.findGroupSources(1L)).thenReturn(List.of());
        assertThat(service.update(1L, 1L, request).getRecipientGroups()).isEmpty();
        verify(repository).deleteSources(1L);
        verify(repository, times(2)).insertGroupSource(anyLong(), anyString());
    }

    @Test
    void groupOnlyCampaignCreatesSnapshotsAndReportsExcludedAddresses() {
        when(repository.findGroupSources(1L)).thenReturn(List.of("ALL_ACCEPTED"));
        when(groups.findGroup(1L, "ALL_ACCEPTED", PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(List.of(candidate("ok@example.org"),
                candidate("blocked@example.org"), candidate("bad")));
        when(repository.findActiveSuppressions(PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(List.of("blocked@example.org"));
        when(repository.updateStatus(1L, 1L, "QUEUED")).thenReturn(1);

        var response = service.prepareJob(1L, 1L, queueRequest());

        assertThat(response.getIncludedCount()).isEqualTo(1);
        assertThat(response.getSuppressionCount()).isEqualTo(1);
        assertThat(response.getInvalidCount()).isEqualTo(1);
        assertThat(response.getTotalCount()).isEqualTo(2);
        verify(repository).insertRecipient(any(), isNull(), eq(1L),
                argThat(r -> r.getNormalizedEmail().equals("ok@example.org")), eq("PENDING"), isNull(), anyString(), anyString(),
                eq(PersonalDataTestSupport.DB_ENC_STRING));
        verify(repository).insertRecipient(any(), isNull(), eq(1L),
                argThat(r -> r.getNormalizedEmail().equals("blocked@example.org")), eq("EXCLUDED"), eq("SUPPRESSED"), anyString(), anyString(),
                eq(PersonalDataTestSupport.DB_ENC_STRING));
        verify(repository, times(2)).insertRecipient(
                any(), any(), anyLong(), any(), anyString(), any(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void zeroEligibleRecipientsAndRepeatedRequestsDoNotCreateJobs() {
        when(repository.findGroupSources(1L)).thenReturn(List.of("ALL_MEMBERS"));
        when(groups.findGroup(1L, "ALL_MEMBERS", PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(List.of(candidate("blocked@example.org")));
        when(repository.findActiveSuppressions(PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(List.of("blocked@example.org"));
        assertThatThrownBy(() -> service.prepareJob(1L, 1L, queueRequest())).isInstanceOf(IllegalArgumentException.class);
        when(repository.findJobByIdempotencyKey("test-key")).thenReturn(10L);
        assertThatThrownBy(() -> service.prepareJob(1L, 1L, queueRequest())).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).insertJob(any(), any(), anyString(), anyString(), anyInt(), anyInt(), any());
        verify(repository, never()).updateStatus(anyLong(), anyLong(), anyString());
    }

    @Test
    void campaignRequestValidatesInheritedGroupValues() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = campaignRequest();
            assertThat(factory.getValidator().validate(request)).isEmpty();
            request.setRecipientGroups(List.of("NOT_A_GROUP"));
            assertThat(factory.getValidator().validate(request)).anyMatch(v -> v.getPropertyPath().toString().startsWith("recipientGroups"));
        }
    }

    private MailCampaignRequest campaignRequest() {
        var request = new MailCampaignRequest();
        request.setSubject("Notice");
        request.setSenderName("Conference");
        request.setSenderEmail("sender@example.org");
        request.setHtmlContent("<p>Notice</p>");
        return request;
    }

    private MailQueueRequest queueRequest() {
        var request = new MailQueueRequest();
        request.setIdempotencyKey("test-key");
        return request;
    }

    private MailRecipientCandidate candidate(String email) {
        return MailRecipientCandidate.builder().email(email).build();
    }
}
