package com.bjworld21.conference.service;

import com.bjworld21.conference.config.MaintenanceMailProperties;
import com.bjworld21.conference.repository.MaintenanceRequestRepository;
import jakarta.mail.internet.MimeMessage;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;

@Service
public class MaintenanceRequestMailService {
    private static final Logger log = LoggerFactory.getLogger(MaintenanceRequestMailService.class);

    private final JavaMailSender mailSender;
    private final MaintenanceMailProperties properties;
    private final MaintenanceRequestRepository repository;

    public MaintenanceRequestMailService(
            @Qualifier("maintenanceMailSender") JavaMailSender mailSender,
            MaintenanceMailProperties properties,
            MaintenanceRequestRepository repository
    ) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.repository = repository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void sendCreatedNotification(MaintenanceRequestCreatedEvent event) {
        for (MaintenanceRequestCreatedEvent.Recipient recipient : event.recipients()) {
            try {
                validateConfiguration();
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
                helper.setFrom(properties.getFromAddress(), properties.getFromName());
                helper.setTo(recipient.email());
                helper.setSubject(properties.getSubjectPrefix() + " #" + event.requestSeq() + " " + event.title());
                helper.setText(buildBody(event, recipient), true);
                mailSender.send(message);
                repository.markNotificationSent(recipient.notificationSeq());
            } catch (Exception exception) {
                String reason = safeFailureReason(exception);
                repository.markNotificationFailed(recipient.notificationSeq(), reason);
                log.error("유지보수 요청 이메일 발송 실패: requestSeq={}, recipientAdminEmail={}",
                        event.requestSeq(), recipient.email(), exception);
            }
        }
    }

    private void validateConfiguration() {
        if (!properties.isEnabled()) throw new IllegalStateException("유지보수 요청 메일 발송이 비활성화되어 있습니다.");
        if (properties.getPassword() == null || properties.getPassword().isBlank()) {
            throw new IllegalStateException("유지보수 요청 SMTP 비밀번호가 설정되지 않았습니다.");
        }
    }

    private String buildBody(MaintenanceRequestCreatedEvent event, MaintenanceRequestCreatedEvent.Recipient recipient) {
        String contentText = Jsoup.parse(event.content()).text();
        String safeContent = HtmlUtils.htmlEscape(contentText.length() > 1200
                ? contentText.substring(0, 1200) + "…" : contentText);
        return """
                <div style="font-family:Arial,'Malgun Gothic',sans-serif;line-height:1.65;color:#1e293b">
                  <p>%s님, 새로운 유지보수 요청이 등록되었습니다.</p>
                  <table style="border-collapse:collapse;width:100%%;max-width:720px">
                    <tr><th style="border:1px solid #cbd5e1;padding:10px;background:#f8fafc;text-align:left">요청번호</th><td style="border:1px solid #cbd5e1;padding:10px">#%d</td></tr>
                    <tr><th style="border:1px solid #cbd5e1;padding:10px;background:#f8fafc;text-align:left">요청자</th><td style="border:1px solid #cbd5e1;padding:10px">%s</td></tr>
                    <tr><th style="border:1px solid #cbd5e1;padding:10px;background:#f8fafc;text-align:left">제목</th><td style="border:1px solid #cbd5e1;padding:10px">%s</td></tr>
                    <tr><th style="border:1px solid #cbd5e1;padding:10px;background:#f8fafc;text-align:left">내용</th><td style="border:1px solid #cbd5e1;padding:10px">%s</td></tr>
                  </table>
                  <p>관리자 화면의 ‘유지보수 요청’ 메뉴에서 확인하고 답변해 주세요.</p>
                </div>
                """.formatted(
                HtmlUtils.htmlEscape(recipient.name()),
                event.requestSeq(),
                HtmlUtils.htmlEscape(event.requesterName()),
                HtmlUtils.htmlEscape(event.title()),
                safeContent
        );
    }

    private String safeFailureReason(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
