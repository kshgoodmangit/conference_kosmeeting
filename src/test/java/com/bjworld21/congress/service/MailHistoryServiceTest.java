package com.bjworld21.congress.service;

import com.bjworld21.congress.config.UploadProperties;
import com.bjworld21.congress.dto.MailHistoryData.*;
import com.bjworld21.congress.entity.*;
import com.bjworld21.congress.repository.MailCampaignRepository;
import com.bjworld21.congress.repository.MailHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MailHistoryServiceTest {
    @TempDir Path directory;
    private MailHistoryRepository histories;
    private MailCampaignRepository campaigns;
    private MailHistoryService service;
    private AtomicReference<Context> context;

    @BeforeEach void setup() {
        histories = mock(MailHistoryRepository.class); campaigns = mock(MailCampaignRepository.class);
        UploadProperties properties = new UploadProperties(); properties.setBaseDirectory(directory.toString());
        service = new MailHistoryService(histories, campaigns, PersonalDataTestSupport.properties(), new MailHtmlSanitizer(), new UploadStorage(properties), new ObjectMapper());
        context = new AtomicReference<>();
        doAnswer(call -> { if (context.get() == null) { Context c = call.getArgument(0); c.setSeq(10L); context.set(c); } return null; }).when(histories).reserve(any(), anyString());
        when(histories.lockRequest(anyLong(), anyLong(), anyString())).thenAnswer(call -> context.get());
        when(histories.adminName(anyLong(), anyString())).thenReturn("관리자");
        when(histories.sources(eq(1L), anyString(), anyList(), anyString())).thenReturn(List.of(source(1, "person@example.org")));
        doAnswer(call -> { ((MailCampaign) call.getArgument(1)).setSeq(20L); return null; }).when(campaigns).insert(anyLong(), any(), anyString());
        doAnswer(call -> { ((MailSendJob) call.getArgument(0)).setSeq(30L); return null; }).when(histories).insertJob(any(), anyLong(), anyString(), anyInt(), anyInt(), anyLong());
        AtomicLong recipientId = new AtomicLong(40);
        doAnswer(call -> { ((MailCampaignRecipient) call.getArgument(0)).setSeq(recipientId.incrementAndGet()); return null; }).when(campaigns).insertRecipient(any(), anyLong(), anyLong(), any(), anyString(), any(), anyString(), anyString(), anyString());
    }

    @Test void deduplicatesAndRetainsEveryOriginWithUnsentStates() {
        var request = request(); request.setSourceSeqs(List.of(1L, 2L, 3L, 4L));
        when(histories.sources(eq(1L), eq("abstracts"), anyList(), anyString())).thenReturn(List.of(
                source(1, " Person@Example.org "), source(2, "person@example.org"), source(3, "bad"), source(4, "blocked@example.org")));
        when(campaigns.findActiveSuppressions(anyString())).thenReturn(List.of("blocked@example.org"));
        Saved saved = service.save(1L, 7L, request, List.of());
        assertThat(saved.selectedCount()).isEqualTo(4); assertThat(saved.duplicateCount()).isEqualTo(1);
        assertThat(saved.invalidCount()).isEqualTo(1); assertThat(saved.suppressionCount()).isEqualTo(1);
        verify(histories).insertJob(any(), eq(20L), eq("history:10"), eq(3), eq(2), eq(7L));
        ArgumentCaptor<MailCampaign> campaign = ArgumentCaptor.forClass(MailCampaign.class);
        verify(campaigns).insert(eq(1L), campaign.capture(), anyString());
        assertThat(campaign.getValue().getStatus()).isEqualTo("SAVED");
        assertThat(campaign.getValue().getCreatedBy()).isEqualTo(7L);
        assertThat(campaign.getValue().getHtmlContent()).doesNotContain("script", "onclick");
        verify(campaigns).insertSendResult(41L, "none", "SAVED");
        verify(campaigns, times(2)).insertSendResult(anyLong(), eq("none"), eq("EXCLUDED"));
        verify(histories).origin(10L, 41L, "ABSTRACT", 1L, "A-1");
        verify(histories).origin(10L, 41L, "ABSTRACT", 2L, "A-2");
        verify(histories, times(4)).origin(anyLong(), anyLong(), anyString(), anyLong(), anyString());
    }

    @Test void retryReturnsSameHistoryAndChangedRequestIsRejected() {
        SaveRequest request = request();
        Saved first = service.save(1L, 7L, request, List.of());
        Context committed = context.get(); committed.setJobSeq(30L); committed.setSelectedCount(1);
        assertThat(service.save(1L, 7L, request, List.of()).seq()).isEqualTo(first.seq());
        verify(campaigns, times(1)).insert(anyLong(), any(), anyString());
        request.setSubject("changed");
        assertThatThrownBy(() -> service.save(1L, 7L, request, List.of())).isInstanceOf(IllegalStateException.class);
    }

    @Test void rejectsDeletedOrOtherConferenceSourcesBeforeSavingMail() {
        when(histories.sources(eq(2L), anyString(), anyList(), anyString())).thenReturn(List.of());
        assertThatThrownBy(() -> service.save(2L, 7L, request(), List.of())).isInstanceOf(IllegalArgumentException.class);
        verify(campaigns, never()).insert(anyLong(), any(), anyString());
    }

    @Test void rejectsUnsupportedMenuEmptyRecipientsAndAllExcludedRecipients() {
        SaveRequest request = request(); request.setSourceMenu("admin");
        assertThatThrownBy(() -> service.save(1L, 7L, request, List.of())).isInstanceOf(IllegalArgumentException.class);
        request.setSourceMenu("abstracts"); request.setSourceSeqs(List.of());
        assertThatThrownBy(() -> service.save(1L, 7L, request, List.of())).isInstanceOf(IllegalArgumentException.class);
        request.setSourceSeqs(List.of(1L));
        when(campaigns.findActiveSuppressions(anyString())).thenReturn(List.of("person@example.org"));
        assertThatThrownBy(() -> service.save(1L, 7L, request, List.of())).isInstanceOf(IllegalArgumentException.class);
        verify(campaigns, never()).insert(anyLong(), any(), anyString());
    }

    @Test void storesMonthlyRelativeAttachmentAndDeletesItOnTransactionRollback() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.save(1L, 7L, request(), List.of(new MockMultipartFile("files", "안내.txt", "text/plain", "내용".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            ArgumentCaptor<MailAttachment> attachment = ArgumentCaptor.forClass(MailAttachment.class);
            verify(campaigns).insertAttachment(attachment.capture());
            assertThat(attachment.getValue().getSavedFilename()).matches("\\d{6}/[0-9a-f-]+\\.txt");
            Path path = directory.resolve("mail").resolve(attachment.getValue().getSavedFilename());
            assertThat(path).exists();
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertThat(path).doesNotExist();
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test void rejectsUnsafeFilesAndCleansPartialFilesAfterDatabaseFailure() throws Exception {
        assertThatThrownBy(() -> service.save(1L, 7L, request(), List.of(new MockMultipartFile("files", "../x.txt", "text/plain", new byte[]{1})))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save(1L, 7L, request(), List.of(new MockMultipartFile("files", "x.exe", "application/octet-stream", new byte[]{1})))).isInstanceOf(IllegalArgumentException.class);
        doThrow(new IllegalStateException("test failure")).when(campaigns).insertAttachment(any());
        assertThatThrownBy(() -> service.save(1L, 7L, request(), List.of(new MockMultipartFile("files", "x.txt", "text/plain", new byte[]{1})))).isInstanceOf(IllegalStateException.class);
        try (var paths = Files.walk(directory)) { assertThat(paths.filter(Files::isRegularFile).count()).isZero(); }
    }

    @Test void paginatesUsingServerSummaryAndValidatesDateOrder() {
        Filter filter = new Filter(); filter.setConferenceSeq(1L);
        Summary summary = new Summary(); summary.setTotalCount(25); summary.setSavedCount(25); summary.setRecipientCount(90);
        when(histories.summary(any(), anyString())).thenReturn(summary);
        Page page = service.page(filter, 999, 20);
        assertThat(page.page()).isEqualTo(2); assertThat(page.totalPages()).isEqualTo(2); assertThat(page.summary().getRecipientCount()).isEqualTo(90);
        verify(histories).page(eq(filter), anyString(), eq(20), eq(20L));
        filter.setDateFrom(LocalDate.of(2026, 9, 18)); filter.setDateTo(LocalDate.of(2026, 9, 17));
        assertThatThrownBy(() -> service.page(filter, 1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.detail(2L, 10L)).isInstanceOf(IllegalArgumentException.class);
        verify(histories, never()).recipients(anyLong(), anyLong(), anyString());
    }

    @Test void emailListUsesDistinctAddressCountForPaginationAndKeepsRecipientStatusFilter() {
        Filter filter = new Filter(); filter.setConferenceSeq(1L); filter.setKeyword(" person ");
        filter.setStatus("EXCLUDED"); filter.setExactEmail("client-value-must-not-narrow-list");
        EmailSummary summary = new EmailSummary(); summary.setTotalCount(21); summary.setHistoryCount(82); summary.setExcludedCount(82);
        when(histories.emailSummary(any(), anyString())).thenReturn(summary);
        EmailPage result = service.emails(filter, 999, 20);
        assertThat(result.page()).isEqualTo(2); assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.summary().getHistoryCount()).isEqualTo(82);
        assertThat(filter.getKeyword()).isEqualTo("person"); assertThat(filter.getExactEmail()).isNull();
        verify(histories).emails(argThat(f -> f.getStatus().equals("EXCLUDED") && f.getConferenceSeq().equals(1L)), anyString(), eq(20), eq(20L));
        assertThatThrownBy(() -> service.page(filter, 1, 20)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void emailDrilldownUsesScopedStoredAddressAndHistoryCountForPagination() {
        Filter filter = new Filter(); filter.setConferenceSeq(1L); filter.setSourceMenu("abstracts");
        filter.setExactEmail("untrusted@example.org");
        when(histories.recipientEmail(eq(1L), eq(41L), anyString())).thenReturn("person+tag@example.org");
        EmailSummary summary = new EmailSummary(); summary.setTotalCount(1); summary.setHistoryCount(42); summary.setSavedCount(40); summary.setExcludedCount(2);
        when(histories.emailSummary(any(), anyString())).thenReturn(summary);
        EmailHistoryItem item = new EmailHistoryItem(); item.setSeq(10L); item.setStatus("EXCLUDED"); item.setExclusionReason("SUPPRESSED");
        when(histories.emailHistories(any(), anyString(), anyInt(), anyLong())).thenReturn(List.of(item));
        EmailHistoryPage result = service.emailHistories(filter, 41L, 999, 20);
        assertThat(result.email()).isEqualTo("person+tag@example.org");
        assertThat(result.page()).isEqualTo(3); assertThat(result.summary().getTotalCount()).isEqualTo(42);
        assertThat(result.items().get(0).getStatus()).isEqualTo("EXCLUDED");
        verify(histories).emailHistories(argThat(f -> f.getExactEmail().equals("person+tag@example.org") && f.getSourceMenu().equals("abstracts")), anyString(), eq(20), eq(40L));
    }

    @Test void rejectsForeignEmailDrilldownBeforeQueryingHistoriesAndAllowsMissingAddressGroup() {
        Filter filter = new Filter(); filter.setConferenceSeq(2L);
        assertThatThrownBy(() -> service.emailHistories(filter, 41L, 1, 20)).isInstanceOf(IllegalArgumentException.class);
        verify(histories, never()).emailSummary(any(), anyString());
        verify(histories, never()).emailHistories(any(), anyString(), anyInt(), anyLong());
        filter.setConferenceSeq(1L);
        when(histories.recipientEmail(eq(1L), eq(42L), anyString())).thenReturn("");
        when(histories.emailSummary(any(), anyString())).thenReturn(new EmailSummary());
        EmailHistoryPage empty = service.emailHistories(filter, 42L, 1, 20);
        assertThat(empty.email()).isEmpty(); assertThat(empty.totalPages()).isEqualTo(1);
        assertThat(filter.getExactEmail()).isEmpty();
    }

    private SaveRequest request() {
        SaveRequest request = new SaveRequest(); request.setRequestKey("223a3c98-5924-43d0-ad59-81d5ae0c40ac");
        request.setSourceMenu("abstracts"); request.setSourceSeqs(List.of(1L)); request.setSubject("안내");
        request.setHtmlContent("<p onclick='alert(1)'>안내 내용</p><script>alert(1)</script>"); return request;
    }
    private static Source source(long seq, String email) {
        Source source = new Source(); source.setSeq(seq); source.setEmail(email); source.setFullName("수신자"); source.setAffiliation("기관"); source.setSourceLabel("A-" + seq); return source;
    }
}
