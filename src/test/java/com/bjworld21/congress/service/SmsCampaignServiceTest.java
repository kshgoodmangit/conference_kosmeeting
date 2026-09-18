package com.bjworld21.congress.service;

import com.bjworld21.congress.dto.SmsData.*;
import com.bjworld21.congress.repository.SmsCampaignRepository;
import com.bjworld21.congress.sms.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SmsCampaignServiceTest {
    private SmsCampaignRepository repository;
    private SmsCampaignService service;
    @BeforeEach void setup() {
        repository = mock(SmsCampaignRepository.class);
        service = new SmsCampaignService(
                repository, new SmsGatewayProperties(), PersonalDataTestSupport.properties()
        );
        Campaign campaign = new Campaign(); campaign.setSeq(1L); campaign.setStatus("DRAFT"); campaign.setVersionNo(0);
        when(repository.find(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(campaign);
        when(repository.lock(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(campaign);
    }
    @Test void canonicalizesDomesticAndInternationalPhonesAndRejectsInvalidValues() {
        for (String value : List.of("010-1234-5678", "+82 10 1234 5678", "+82 (0)10-1234-5678", "821012345678", "00821012345678")) {
            assertThat(SmsPhoneNumbers.recipient(value)).isEqualTo("+821012345678");
        }
        assertThat(SmsPhoneNumbers.recipient("+1 (415) 555-1234")).isEqualTo("+14155551234");
        for (String value : List.of("", "abc01012345678", "1234", "0101234567", "+8212345678", "01012345678 ext 1", "++14155551234")) assertThat(SmsPhoneNumbers.recipient(value)).isNull();
        assertThat(SmsPhoneNumbers.sender("02-1234-5678")).isEqualTo("0212345678");
        assertThat(SmsPhoneNumbers.sender("02-abc-1234")).isNull();
    }
    @Test void snapshotsDistinctPhonesAndExcludedCandidates() {
        var result = SmsCampaignService.prepareRecipients(List.of(candidate("010-1234-5678"),candidate("+821012345678"),candidate("01099998888"),candidate(null)), Set.of("+821099998888"));
        assertThat(result.includedCount()).isEqualTo(1);
        assertThat(result.duplicateCount()).isEqualTo(1);
        assertThat(result.suppressionCount()).isEqualTo(1);
        assertThat(result.invalidCount()).isEqualTo(1);
        assertThat(result.recipients()).extracting(Recipient::getStatus).containsExactly("READY","EXCLUDED","EXCLUDED");
    }
    @Test void groupOnlyPreparationStaysPreparedAndReplaysSameKeyWithoutAnotherJob() {
        Source group = new Source(); group.setSourceType("GROUP"); group.setGroupCode("ALL_ACCEPTED");
        when(repository.sources(1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(List.of(group));
        when(repository.group(1L, "ALL_ACCEPTED", PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(List.of(candidate("01012345678")));
        when(repository.markPrepared(1L, 1L)).thenReturn(1);
        doAnswer(invocation -> { ((Job) invocation.getArgument(0)).setSeq(20L); return null; }).when(repository).insertJob(any());
        PrepareRequest request = new PrepareRequest(); request.setIdempotencyKey("test-key");
        Job job = service.prepare(1L, 1L, request);
        assertThat(job.getStatus()).isEqualTo("PREPARED"); assertThat(job.getProvider()).isEqualTo("none");
        assertThat(job.getIncludedCount()).isEqualTo(1);
        when(repository.job(1L)).thenReturn(job);
        assertThat(service.prepare(1L, 1L, request)).isSameAs(job);
        verify(repository, times(1)).insertJob(any());
        verify(repository).insertRecipients(eq(20L), anyList(), eq(PersonalDataTestSupport.DB_ENC_STRING));
    }
    @Test void emptyOrSuppressedSelectionNeverCreatesJob() {
        assertThatThrownBy(() -> service.prepare(1L, 1L, prepareRequest())).isInstanceOf(IllegalArgumentException.class);
        Source group = new Source(); group.setSourceType("GROUP"); group.setGroupCode("ALL_MEMBERS");
        when(repository.sources(1L, PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(List.of(group));
        when(repository.group(1L, "ALL_MEMBERS", PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(List.of(candidate("01012345678")));
        when(repository.suppressedPhones(PersonalDataTestSupport.DB_ENC_STRING)).thenReturn(List.of("+821012345678"));
        assertThatThrownBy(() -> service.prepare(1L, 1L, prepareRequest())).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).insertJob(any());
    }
    @Test void savesGroupsAndDeduplicatesDirectNumbersAndRejectsStaleUpdates() {
        CampaignRequest request = campaignRequest(); request.setRecipientGroups(List.of("ALL_MEMBERS"));
        DirectRecipient first = new DirectRecipient(); first.setPhoneNumber("01012345678");
        DirectRecipient second = new DirectRecipient(); second.setPhoneNumber("+821012345678");
        request.setDirectRecipients(List.of(first, second));
        doAnswer(invocation -> { ((Campaign) invocation.getArgument(1)).setSeq(1L); return null; })
                .when(repository).insert(eq(1L), any(), eq(PersonalDataTestSupport.DB_ENC_STRING));
        service.create(1L, request, 7L);
        verify(repository).insertSource(eq(1L), argThat(source -> "ALL_MEMBERS".equals(source.getGroupCode())),
                eq(PersonalDataTestSupport.DB_ENC_STRING));
        verify(repository, times(1)).insertSource(eq(1L), argThat(source -> "DIRECT".equals(source.getSourceType())),
                eq(PersonalDataTestSupport.DB_ENC_STRING));
        request.setVersionNo(0);
        assertThatThrownBy(() -> service.update(1L, 1L, request)).isInstanceOf(IllegalStateException.class);
        verify(repository, never()).deleteSources(anyLong());
    }
    @Test void preparedCampaignCannotBeEditedDeletedOrReprepared() {
        Campaign campaign = repository.lock(1L, 1L, PersonalDataTestSupport.DB_ENC_STRING); campaign.setStatus("PREPARED");
        assertThatThrownBy(() -> service.update(1L, 1L, campaignRequest())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.delete(1L, 1L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.prepare(1L, 1L, prepareRequest())).isInstanceOf(IllegalStateException.class);
    }
    @Test void providerRegistryFailsClosedAndDetectsDuplicateAdapters() {
        SmsGatewayProperties properties = new SmsGatewayProperties();
        SmsGatewayAdapter adapter = mock(SmsGatewayAdapter.class); when(adapter.providerKey()).thenReturn("example");
        SmsGatewayRegistry registry = new SmsGatewayRegistry(List.of(adapter), properties);
        assertThatThrownBy(registry::requireEnabledAdapter).isInstanceOf(IllegalStateException.class);
        properties.setEnabled(true);
        assertThatThrownBy(registry::requireEnabledAdapter).isInstanceOf(IllegalStateException.class);
        properties.setProvider("example"); assertThat(registry.requireEnabledAdapter()).isSameAs(adapter);
        verify(adapter, never()).send(any());
        assertThatThrownBy(() -> new SmsGatewayRegistry(List.of(adapter, adapter), properties)).isInstanceOf(IllegalStateException.class);
    }
    @Test void invalidGroupCannotBeSaved() {
        CampaignRequest request = campaignRequest(); request.setRecipientGroups(List.of("ALL_ADMINS"));
        assertThatThrownBy(() -> service.create(1L, request, 7L)).isInstanceOf(IllegalArgumentException.class);
    }
    private Candidate candidate(String phone) { Candidate value = new Candidate(); value.setPhoneNumber(phone); return value; }
    private PrepareRequest prepareRequest() { PrepareRequest value = new PrepareRequest(); value.setIdempotencyKey("key"); return value; }
    private CampaignRequest campaignRequest() { CampaignRequest value = new CampaignRequest(); value.setTitle("Notice"); value.setMessage("Conference notice"); value.setSenderNumber("0212345678"); return value; }
}
