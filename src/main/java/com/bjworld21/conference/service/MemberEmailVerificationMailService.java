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
public class MemberEmailVerificationMailService {
    private final JavaMailSender sender;
    private final MaintenanceMailProperties properties;

    public MemberEmailVerificationMailService(@Qualifier("maintenanceMailSender") JavaMailSender sender,
                                               MaintenanceMailProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    public void checkAvailable() {
        if (!properties.isEnabled() || properties.getPassword() == null || properties.getPassword().isBlank()
                || properties.getFromAddress() == null || properties.getFromAddress().isBlank()) {
            throw new IllegalStateException("Email verification mail is unavailable");
        }
    }

    public void sendCode(String email, String eventName, String code, long minutes)
            throws MessagingException, UnsupportedEncodingException {
        sendCode(email, eventName, code, minutes, "en");
    }

    public void sendCode(String email, String eventName, String code, long minutes, String language)
            throws MessagingException, UnsupportedEncodingException {
        checkAvailable();
        if (code == null || !code.matches("[0-9]{6}")) throw new IllegalArgumentException("Invalid verification code");
        String name = eventName == null || eventName.isBlank() ? "Conference" : eventName.replaceAll("[\\r\\n]", " ");
        var message = sender.createMimeMessage();
        var helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED, StandardCharsets.UTF_8.name());
        helper.setFrom(properties.getFromAddress(), name);
        helper.setTo(email);
        boolean korean = "ko".equals(language);
        helper.setSubject("[" + name + "] " + (korean ? "이메일 주소 인증" : "Verify your email address"));
        String plain = "Verify your " + name + " email address\n\nYour verification code: " + code
                + "\n\nEnter this code on the sign-up page. This code expires in " + minutes + " minutes."
                + "\nUse the latest code if you request another email. Do not share this code."
                + "\nIf you did not request this, you can ignore this email. No account has been created by this request.";
        String html = """
                <div style="font-family:Arial,sans-serif;line-height:1.7;color:#333;max-width:600px">
                  <h2>%s — Verify your email address</h2>
                  <p>Use the verification code below to confirm your email address on the sign-up page.</p>
                  <p style="display:inline-block;padding:12px 24px;background:#eef7ff;color:#2457d6;border-radius:4px;font-size:32px;font-weight:bold;letter-spacing:8px">%s</p>
                  <p>This code expires in %d minutes. Use the latest code if you request another email.</p>
                  <p>Do not share this code with anyone.</p>
                  <p>If you did not request this, you can ignore this email. No account has been created by this request.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(name), code, minutes);
        if (korean) {
            plain = name + " 이메일 인증\n\n인증번호: " + code + "\n\n회원가입 화면에 인증번호를 입력해 주세요. "
                    + minutes + "분 동안 유효합니다. 재발송한 경우 가장 최근 번호를 사용하세요.\n"
                    + "인증번호를 다른 사람에게 알려주지 마세요. 요청하지 않았다면 이 메일을 무시하셔도 됩니다.";
            html = "<div><h2>" + HtmlUtils.htmlEscape(name) + " 이메일 인증</h2><p>인증번호: <strong>" + code
                    + "</strong></p><p>" + minutes + "분 동안 유효합니다. 가장 최근 번호를 사용하세요.</p>"
                    + "<p>인증번호를 다른 사람에게 알려주지 마세요. 요청하지 않았다면 이 메일을 무시하셔도 됩니다.</p></div>";
        }
        helper.setText(plain, html);
        sender.send(message);
    }
}
