package com.bjworld21.congress.service;

import com.bjworld21.congress.config.MaintenanceMailProperties;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Service
public class MemberPasswordResetMailService {
    private final JavaMailSender sender;
    private final MaintenanceMailProperties properties;

    public MemberPasswordResetMailService(@Qualifier("maintenanceMailSender") JavaMailSender sender,
                                         MaintenanceMailProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    public void checkAvailable() {
        if (!properties.isEnabled() || properties.getPassword() == null || properties.getPassword().isBlank()
                || properties.getFromAddress() == null || properties.getFromAddress().isBlank()) {
            throw new IllegalStateException("Password recovery mail is unavailable");
        }
    }

    public void sendResetLink(String email, String eventName, String link, long minutes)
            throws MessagingException, UnsupportedEncodingException {
        checkAvailable();
        String safeName = eventName == null || eventName.isBlank() ? "Conference" : eventName.replaceAll("[\\r\\n]", " ");
        var message = sender.createMimeMessage();
        var helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED, StandardCharsets.UTF_8.name());
        helper.setFrom(properties.getFromAddress(), safeName);
        helper.setTo(email);
        helper.setSubject("[" + safeName + "] Reset your password");
        String plain = "Reset your " + safeName + " password\n\nOpen this link to choose a new password:\n"
                + link + "\n\nThis link expires in " + minutes + " minutes and can be used once."
                + "\nIf you did not request this, you can ignore this email. Your password has not changed.";
        String html = """
                <div style="font-family:Arial,sans-serif;line-height:1.7;color:#333;max-width:600px">
                  <h2>%s — Reset your password</h2>
                  <p>We received a request to reset your account password.</p>
                  <p><a href="%s" style="display:inline-block;padding:12px 24px;background:#2457d6;color:white;text-decoration:none;border-radius:4px">Reset Password</a></p>
                  <p>This link expires in %d minutes and can be used once.</p>
                  <p>If the button does not work, copy this link into your browser:</p>
                  <p style="word-break:break-all">%s</p>
                  <p>If you did not request this, you can ignore this email. Your password has not changed.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(safeName), HtmlUtils.htmlEscape(link), minutes, HtmlUtils.htmlEscape(link));
        helper.setText(plain, html);
        sender.send(message);
    }
}
