package com.bjworld21.conference.service;

import com.bjworld21.conference.config.MaintenanceMailProperties;
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
        sendResetLink(email, eventName, link, minutes, "en");
    }

    public void sendResetLink(String email, String eventName, String link, long minutes, String language)
            throws MessagingException, UnsupportedEncodingException {
        checkAvailable();
        String safeName = eventName == null || eventName.isBlank() ? "Conference" : eventName.replaceAll("[\\r\\n]", " ");
        var message = sender.createMimeMessage();
        var helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED, StandardCharsets.UTF_8.name());
        helper.setFrom(properties.getFromAddress(), safeName);
        helper.setTo(email);
        boolean korean = "ko".equals(language);
        helper.setSubject("[" + safeName + "] " + (korean ? "비밀번호 재설정" : "Reset your password"));
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
        if (korean) {
            plain = safeName + " 비밀번호 재설정\n\n아래 링크에서 새 비밀번호를 설정하세요.\n" + link
                    + "\n\n이 링크는 " + minutes + "분 동안 유효하며 한 번만 사용할 수 있습니다."
                    + "\n요청하지 않았다면 이 메일을 무시하셔도 됩니다. 비밀번호는 변경되지 않았습니다.";
            html = "<div><h2>" + HtmlUtils.htmlEscape(safeName) + " 비밀번호 재설정</h2><p><a href=\""
                    + HtmlUtils.htmlEscape(link) + "\">비밀번호 재설정</a></p><p>이 링크는 " + minutes
                    + "분 동안 유효하며 한 번만 사용할 수 있습니다.</p>"
                    + "<p>요청하지 않았다면 이 메일을 무시하셔도 됩니다. 비밀번호는 변경되지 않았습니다.</p></div>";
        }
        helper.setText(plain, html);
        sender.send(message);
    }
}
