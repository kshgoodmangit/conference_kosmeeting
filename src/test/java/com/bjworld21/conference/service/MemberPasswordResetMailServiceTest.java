package com.bjworld21.conference.service;

import com.bjworld21.conference.config.MaintenanceMailProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberPasswordResetMailServiceTest {
    @Test
    void reusesConfiguredSenderAndBuildsPlainAndHtmlRecoveryMail() throws Exception {
        var sender = mock(JavaMailSender.class);
        var settings = new MaintenanceMailProperties();
        settings.setPassword("test-only-smtp-password");
        settings.setFromAddress("sender@example.com");
        var message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        new MemberPasswordResetMailService(sender, settings).sendResetLink("member@example.com", "APDRC8 <test>",
                "https://conference.example/reset-password#token=" + "x".repeat(43), 30);
        verify(sender).send(message);
        assertThat(message.getSubject()).isEqualTo("[APDRC8 <test>] Reset your password");
        assertThat(message.getFrom()[0].toString()).contains("sender@example.com");
        var root = (MimeMultipart) message.getContent();
        var alternatives = (MimeMultipart) root.getBodyPart(0).getContent();
        String plain = alternatives.getBodyPart(0).getContent().toString();
        String html = alternatives.getBodyPart(1).getContent().toString();
        assertThat(plain).contains("30 minutes", "Your password has not changed.");
        assertThat(html).contains("APDRC8 &lt;test&gt;", "Reset Password").doesNotContain("test-only-smtp-password");
    }

    @Test
    void disabledOrMissingCredentialsCannotSendMail() {
        var sender = mock(JavaMailSender.class);
        var settings = new MaintenanceMailProperties();
        var service = new MemberPasswordResetMailService(sender, settings);
        assertThatThrownBy(service::checkAvailable).isInstanceOf(IllegalStateException.class);
        settings.setPassword("test");
        settings.setEnabled(false);
        assertThatThrownBy(service::checkAvailable).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(sender);
    }

    @Test
    void koreanRecoveryMailPreservesConferenceLanguageLinkAndEscapesEventName() throws Exception {
        var sender = mock(JavaMailSender.class);
        var settings = new MaintenanceMailProperties();
        settings.setPassword("test-only-secret");
        settings.setFromAddress("sender@example.com");
        var message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        String link = "https://conference.example/apdrc8/ko/reset-password#token=" + "x".repeat(43);
        new MemberPasswordResetMailService(sender, settings).sendResetLink("member@example.com", "APDRC8 <test>", link, 30, "ko");
        assertThat(message.getSubject()).isEqualTo("[APDRC8 <test>] 비밀번호 재설정");
        var alternatives = (MimeMultipart) ((MimeMultipart) message.getContent()).getBodyPart(0).getContent();
        assertThat(alternatives.getBodyPart(0).getContent().toString()).contains(link, "30분");
        assertThat(alternatives.getBodyPart(1).getContent().toString()).contains(link, "APDRC8 &lt;test&gt;")
                .doesNotContain("test-only-secret");
    }
}
