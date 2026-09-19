package com.bjworld21.conference.service;

import com.bjworld21.conference.config.*;
import com.bjworld21.conference.entity.*;
import com.bjworld21.conference.repository.AdminAccessRequestRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminAccessRequestMailServiceTest {
    @Test void escapesUserInputAndLinksToStoredSite() {
        var request = new AdminAccessRequest(); request.setSeq(1L); request.setSiteUrl("https://conference.example");
        request.setPurpose("<script>alert(1)</script>"); request.setRequesterName("<img src=x>");
        request.setExpiresAt(LocalDateTime.of(2026,9,17,12,0));
        var personal = new PersonalDataProperties(); personal.setDbEncString("test-key");
        var clock = Clock.systemUTC();
        var service = new AdminAccessRequestMailService(null,null,null,personal,clock,new AdminAccessRequestMailLink(personal,clock));
        String html = service.buildBody(request);
        assertThat(html).contains("도메인: <a href=\"https://conference.example\">https://conference.example</a>");
        assertThat(html).contains("https://conference.example/admin/access-request-approval?seq=1&amp;expires=", "&amp;signature=", "요청 확인 및 접근 허용</a>");
        assertThat(html).doesNotContain("관리자 바로가기", "로그인 후 시스템관리");
        assertThat(html).doesNotContain("/admin/access-requests");
        assertThat(html).contains("&lt;script&gt;", "&lt;img").doesNotContain("<script>","<img src=x>");
    }
    @Test void smtpFailureIsRecordedAndThirdFailureStopsRetry() {
        var repository = mock(AdminAccessRequestRepository.class);
        var sender = mock(JavaMailSender.class);
        var mailProps = new MaintenanceMailProperties(); mailProps.setPassword("test-only");
        var personal = new PersonalDataProperties(); personal.setDbEncString("test-key");
        var clock = Clock.fixed(Instant.parse("2026-09-16T03:00:00Z"),ZoneId.of("Asia/Seoul"));
        var now = LocalDateTime.now(clock);
        var mail = new AdminAccessRequestMail(); mail.setSeq(1L); mail.setRequestSeq(2L); mail.setRecipientEmail("ops@example.com");
        var request = new AdminAccessRequest(); request.setSeq(2L); request.setSiteUrl("https://conference.example");
        request.setExpiresAt(now.plusHours(24));
        when(repository.dueMail(now)).thenReturn(List.of(1L));
        when(repository.claimMail(eq(1L),anyString(),eq(now),any())).thenAnswer(call -> {
            mail.setClaimToken(call.getArgument(1)); return 1;
        });
        when(repository.findMail(1L,"test-key")).thenReturn(mail);
        when(repository.find(2L,"test-key",now)).thenReturn(request);
        when(sender.createMimeMessage()).thenAnswer(call -> new MimeMessage((Session)null));
        doThrow(new MailSendException("test SMTP failure")).when(sender).send(any(MimeMessage.class));
        var service = new AdminAccessRequestMailService(repository,sender,mailProps,personal,clock,new AdminAccessRequestMailLink(personal,clock));
        mail.setAttemptCount(1); service.sendPending();
        verify(repository).finishMail(eq(1L),anyString(),eq("READY"),eq(now.plusMinutes(5)),anyString());
        mail.setAttemptCount(3); service.sendPending();
        verify(repository).finishMail(eq(1L),anyString(),eq("FAILED"),any(),anyString());
        doNothing().when(sender).send(any(MimeMessage.class));
        service.sendPending();
        verify(repository).finishMail(eq(1L),anyString(),eq("SENT"),eq(now),isNull());
        clearInvocations(sender);
        when(repository.claimMail(eq(1L),anyString(),eq(now),any())).thenReturn(0);
        service.sendPending();
        verifyNoInteractions(sender);
        verify(repository, times(4)).dueMail(now);
    }
}
