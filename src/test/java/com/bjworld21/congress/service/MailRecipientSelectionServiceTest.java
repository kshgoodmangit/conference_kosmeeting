package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.MailDirectRecipientRequest;
import com.bjworld21.congress.dto.MailRecipientSelectionRequest;
import com.bjworld21.congress.entity.MailRecipientCandidate;
import com.bjworld21.congress.repository.MailCampaignRepository;
import com.bjworld21.congress.repository.MailRecipientGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MailRecipientSelectionServiceTest {
    private MailRecipientGroupRepository groups;
    private MailCampaignRepository campaigns;
    private MailAddressBookService addressBooks;
    private MailRecipientSelectionService service;

    @BeforeEach
    void setUp() {
        groups = mock(MailRecipientGroupRepository.class);
        campaigns = mock(MailCampaignRepository.class);
        addressBooks = mock(MailAddressBookService.class);
        service = new MailRecipientSelectionService(
                groups, campaigns, addressBooks, PersonalDataTestSupport.properties()
        );
    }

    @Test
    void previewAndPreparationDeduplicateAllSourcesAndPreserveContactOwnership() {
        when(groups.findGroup(1L, "ALL_MEMBERS", PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(List.of(candidate(" User@example.org "), candidate("blocked@example.org")));
        when(groups.findGroup(1L, "ALL_ACCEPTED", PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(List.of(candidate("USER@example.org")));
        var contact = candidate("user@example.org");
        contact.setContactSeq(12L);
        when(groups.findAddressBooks(List.of(1L), PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(List.of(contact));
        when(campaigns.findActiveSuppressions(PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(List.of(" BLOCKED@example.org "));
        var request = new MailRecipientSelectionRequest();
        request.setRecipientGroups(List.of("ALL_MEMBERS", "ALL_ACCEPTED"));
        request.setAddressBookSeqs(List.of(1L));
        var direct = new MailDirectRecipientRequest();
        direct.setEmail("user@example.org");
        request.setDirectRecipients(List.of(direct));

        var preview = service.preview(1L, request);
        var resolved = service.resolve(1L, request);

        assertThat(preview.includedCount()).isEqualTo(1).isEqualTo(resolved.includedCount());
        assertThat(preview.duplicateCount()).isEqualTo(3).isEqualTo(resolved.duplicateCount());
        assertThat(preview.suppressionCount()).isEqualTo(1).isEqualTo(resolved.suppressionCount());
        assertThat(preview.groupCounts()).containsEntry("ALL_MEMBERS", 1).containsEntry("ALL_ACCEPTED", 1);
        assertThat(resolved.recipients()).filteredOn(r -> r.getNormalizedEmail().equals("user@example.org"))
                .singleElement().extracting(MailRecipientCandidate::getContactSeq).isEqualTo(12L);
        verify(addressBooks, times(2)).requireAddressBook(1L);
    }

    @Test
    void excludesMissingAndMalformedAddressesWithoutDiscardingValidRecipients() {
        when(groups.findGroup(1L, "ALL_REGISTRANTS", PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(List.of(
                candidate(null), candidate(""), candidate("bad@"), candidate("a b@example.org"), candidate("valid@example.org")));
        var request = new MailRecipientSelectionRequest();
        request.setRecipientGroups(List.of("ALL_REGISTRANTS"));

        var selection = service.resolve(1L, request);

        assertThat(selection.includedCount()).isEqualTo(1);
        assertThat(selection.invalidCount()).isEqualTo(4);
        assertThat(selection.recipients()).singleElement().extracting(MailRecipientCandidate::getEmail).isEqualTo("valid@example.org");
    }

    @Test
    void preparationResolvesCurrentGroupMembershipAgain() {
        when(groups.findGroup(1L, "ALL_ACCEPTED", PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(List.of(candidate("before@example.org")), List.of(candidate("after@example.org")));
        var request = new MailRecipientSelectionRequest();
        request.setRecipientGroups(List.of("ALL_ACCEPTED"));

        service.preview(1L, request);
        var selection = service.resolve(1L, request);

        assertThat(selection.recipients()).singleElement().extracting(MailRecipientCandidate::getEmail).isEqualTo("after@example.org");
    }

    @Test
    void supportsLegacyEmptySelectionAndRejectsUnknownGroups() {
        var request = new MailRecipientSelectionRequest();
        assertThat(service.resolve(1L, request).includedCount()).isZero();
        request.setRecipientGroups(null);
        assertThat(service.resolve(1L, request).includedCount()).isZero();
        request.setRecipientGroups(List.of("ALL_ADMINS"));
        assertThatThrownBy(() -> service.resolve(1L, request)).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.validateGroups(List.of("ALL_MEMBERS", "ALL_MEMBERS"))).containsExactly("ALL_MEMBERS");
    }

    private MailRecipientCandidate candidate(String email) {
        return MailRecipientCandidate.builder().email(email).build();
    }
}
