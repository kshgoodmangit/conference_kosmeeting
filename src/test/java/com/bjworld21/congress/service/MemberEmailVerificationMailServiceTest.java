package com.bjworld21.congress.service;

import com.bjworld21.congress.config.MaintenanceMailProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import java.util.Properties;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberEmailVerificationMailServiceTest {
    @Test
    void sendsCodeUsingConfiguredGmailSenderWithRecoveryStyleAndPlainFallback() throws Exception {
        var sender = mock(JavaMailSender.class);
        var properties = new MaintenanceMailProperties();
        properties.setPassword("test-only-secret");
        properties.setFromAddress("sender@example.com");
        var message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        new MemberEmailVerificationMailService(sender, properties).sendCode("member@example.com", "APDRC8 <test>", "001234", 10);
        verify(sender).send(message);
        assertThat(message.getSubject()).isEqualTo("[APDRC8 <test>] Verify your email address");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("member@example.com");
        assertThat(message.getFrom()[0].toString()).contains("sender@example.com");
        var parts = (MimeMultipart) ((MimeMultipart) message.getContent()).getBodyPart(0).getContent();
        assertThat(parts.getBodyPart(0).getContent().toString()).contains("001234", "10 minutes", "Do not share");
        assertThat(parts.getBodyPart(1).getContent().toString())
                .contains("APDRC8 &lt;test&gt;", "001234", "#2457d6", "Arial,sans-serif")
                .doesNotContain("test-only-secret");
    }

    @Test
    void unavailableConfigurationCannotSend() {
        var sender = mock(JavaMailSender.class);
        var properties = new MaintenanceMailProperties();
        var mail = new MemberEmailVerificationMailService(sender, properties);
        assertThatThrownBy(mail::checkAvailable).isInstanceOf(IllegalStateException.class);
        properties.setPassword("test");
        properties.setEnabled(false);
        assertThatThrownBy(mail::checkAvailable).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(sender);
    }
}
