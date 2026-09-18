package com.bjworld21.congress.service;

import com.bjworld21.congress.config.*;
import com.bjworld21.congress.dto.*;
import com.bjworld21.congress.entity.*;
import com.bjworld21.congress.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminAccessRequestServiceTest {
    private final AdminAccessRequestRepository repository = mock(AdminAccessRequestRepository.class);
    private final AdminAccountRepository accounts = mock(AdminAccountRepository.class);
    private final AdminIpAllowlistRepository rules = mock(AdminIpAllowlistRepository.class);
    private final AdminIpAllowlistService allowlist = mock(AdminIpAllowlistService.class);
    private final AdminAccessRequestProperties settings = new AdminAccessRequestProperties();
    private final String site = "https://conference.example";
    private final String ip = "203.0.113.10";
    private final LocalDate today = LocalDate.of(2026,9,16);
    private final LocalDateTime now = today.atTime(12,0);
    private AdminAccessRequestService service;

    @BeforeEach void setUp() {
        var personal = new PersonalDataProperties(); personal.setDbEncString("test-key");
        service = new AdminAccessRequestService(repository, accounts, rules, allowlist, settings, personal,
                Clock.fixed(now.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul")));
    }
    private AdminAccessRequestInput input() {
        return new AdminAccessRequestInput(" 소속 ", " 이름 ", "010-1234-5678", " 목적 ", today, today.plusDays(2));
    }
    @Test void duplicateDoesNotSaveOrSendAgain() {
        when(repository.countPending(site,ip,now)).thenReturn(1L);
        assertThat(service.submit(input(),ip,site).duplicate()).isTrue();
        var order = inOrder(repository);
        order.verify(repository).ensureLock(site,ip);
        order.verify(repository).lock(site,ip);
        order.verify(repository).countPending(site,ip,now);
        verify(repository,never()).insert(any(),any());
        verifyNoInteractions(accounts);
    }
    @Test void enforcesCooldownAndDailyLimit() {
        when(repository.countSince(site,ip,now.minusMinutes(10))).thenReturn(1L);
        assertThatThrownBy(() -> service.submit(input(),ip,site)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(429));
        when(repository.countSince(site,ip,now.minusMinutes(10))).thenReturn(0L);
        when(repository.countSince(site,ip,today.atStartOfDay())).thenReturn(3L);
        assertThatThrownBy(() -> service.submit(input(),ip,site)).isInstanceOf(ResponseStatusException.class);
        verify(repository,never()).insert(any(),any());
    }
    @Test void savesRequestAndDeduplicatesMaintenanceRecipients() {
        when(accounts.findAllActiveByRole("maintenance","test-key")).thenReturn(List.of(
                AdminAccount.builder().contactEmail("ops@example.com").build(),
                AdminAccount.builder().contactEmail("OPS@example.com").build(),
                AdminAccount.builder().contactEmail(null).build()));
        doAnswer(call -> { ((AdminAccessRequest)call.getArgument(0)).setSeq(4L); return null; }).when(repository).insert(any(),any());
        assertThat(service.submit(input(),ip,site).duplicate()).isFalse();
        var capture = ArgumentCaptor.forClass(AdminAccessRequest.class);
        verify(repository).insert(capture.capture(),eq("test-key"));
        assertThat(capture.getValue().getAffiliation()).isEqualTo("소속");
        assertThat(capture.getValue().getSiteUrl()).isEqualTo(site);
        assertThat(capture.getValue().getExpiresAt()).isEqualTo(now.plusHours(24));
        verify(repository,times(1)).enqueueMail(4L,"ops@example.com","test-key",now);
    }
    @Test void rejectsInvalidDatesAndSiteUrls() {
        assertThatThrownBy(() -> service.submit(new AdminAccessRequestInput("소속","이름","010-1234-5678","목적",today.minusDays(1),today),ip,site))
                .isInstanceOf(IllegalArgumentException.class);
        for (String url : List.of("javascript:alert(1)","https://user@example.com","https://example.com/?next=bad","https://example.com/admin")) {
            assertThatThrownBy(() -> service.submit(input(),ip,url)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(repository);
    }
    private AdminAccessRequest pending() {
        var request = new AdminAccessRequest(); request.setSeq(4L); request.setStatus("REQUESTED");
        request.setRequestIp(ip); request.setStartDate(today); request.setEndDate(today.plusDays(2));
        when(repository.lockRequest(4L)).thenReturn(4L);
        when(repository.find(4L,"test-key",now)).thenReturn(request);
        return request;
    }
    @Test void approvalUsesRequestedIpAndPeriod() {
        pending();
        when(allowlist.create(any(),eq(9L))).thenReturn(AdminIpAllowlistResponse.builder().seq(20L).build());
        service.approve(4L,9L);
        var capture = ArgumentCaptor.forClass(AdminIpAllowlistRequest.class);
        verify(allowlist).create(capture.capture(),eq(9L));
        assertThat(capture.getValue().getIpCidr()).isEqualTo(ip);
        assertThat(capture.getValue().getUseEndDate()).isEqualTo(today.plusDays(2));
        verify(repository).process(4L,"APPROVED",20L,9L,now);
    }
    @Test void repeatedApprovalIsIdempotentAndExpiredRequestCannotBeApproved() {
        var request = pending(); request.setStatus("APPROVED"); service.approve(4L,9L);
        verifyNoInteractions(allowlist);
        request.setStatus("EXPIRED");
        assertThatThrownBy(() -> service.approve(4L,9L)).isInstanceOf(ResponseStatusException.class);
        verify(repository,never()).process(any(),any(),any(),any(),any());
    }
    @Test void conflictingExistingRuleIsNotOverwritten() {
        pending();
        var existing = AdminIpAllowlist.builder().seq(10L).enabled(false).build();
        when(rules.findByIpCidrForUpdate(ip+"/32")).thenReturn(existing);
        assertThatThrownBy(() -> service.approve(4L,9L)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        verifyNoInteractions(allowlist);
        existing.setEnabled(true);
        service.approve(4L,9L);
        verify(repository).process(4L,"APPROVED",10L,9L,now);
    }
    @Test void rejectionDoesNotGrantAccessAndUnknownRequestIsNotVisible() {
        pending(); service.reject(4L,9L);
        verifyNoInteractions(allowlist,rules);
        verify(repository).process(4L,"REJECTED",null,9L,now);
        assertThatThrownBy(() -> service.approve(99L,9L)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    @Test void approvalAcceptsRequestRecordedOnAnotherSite() {
        pending().setSiteUrl("https://other.example");
        when(allowlist.create(any(),eq(9L))).thenReturn(AdminIpAllowlistResponse.builder().seq(20L).build());
        service.approve(4L,9L);
        verify(repository).process(4L,"APPROVED",20L,9L,now);
    }
}
