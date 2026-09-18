package com.bjworld21.congress.service;

import com.bjworld21.congress.config.MaintenanceMailProperties;
import com.bjworld21.congress.config.PersonalDataProperties;
import com.bjworld21.congress.entity.AdminAccessRequest;
import com.bjworld21.congress.repository.AdminAccessRequestRepository;
import com.bjworld21.congress.security.RequestSiteUrlResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AdminAccessRequestMailService {
    private static final Logger log = LoggerFactory.getLogger(AdminAccessRequestMailService.class);
    private final AdminAccessRequestRepository repository;
    private final JavaMailSender sender;
    private final MaintenanceMailProperties mailProperties;
    private final PersonalDataProperties personalData;
    private final Clock clock;
    private final AdminAccessRequestMailLink mailLink;

    public AdminAccessRequestMailService(AdminAccessRequestRepository repository,
            @Qualifier("maintenanceMailSender") JavaMailSender sender, MaintenanceMailProperties mailProperties,
            PersonalDataProperties personalData, Clock clock, AdminAccessRequestMailLink mailLink) {
        this.repository=repository; this.sender=sender; this.mailProperties=mailProperties;
        this.personalData=personalData; this.clock=clock;
        this.mailLink = mailLink;
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void sendPending() {
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            repository.failAbandonedMail(now);
            for (Long seq : repository.dueMail(now)) sendOne(seq);
        } catch (Exception e) {
            log.error("접근 허용 요청 메일 작업 처리 실패: {}", e.getClass().getSimpleName());
        }
    }

    private void sendOne(Long seq) {
        LocalDateTime now = LocalDateTime.now(clock);
        String token = UUID.randomUUID().toString();
        if (repository.claimMail(seq, token, now, now.plusMinutes(5)) == 0) return;
        var mail = repository.findMail(seq, personalData.requireDbEncString());
        if (mail == null || !token.equals(mail.getClaimToken())) return;
        try {
            if (!mailProperties.isEnabled() || mailProperties.getPassword() == null || mailProperties.getPassword().isBlank()) {
                throw new IllegalStateException("메일 발송 설정 확인 필요");
            }
            var request = repository.find(mail.getRequestSeq(), personalData.requireDbEncString(), now);
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(mailProperties.getFromAddress(), mailProperties.getFromName());
            helper.setTo(mail.getRecipientEmail());
            helper.setSubject("[접근 허용 요청] #" + request.getSeq() + " " + java.net.URI.create(request.getSiteUrl()).getAuthority());
            helper.setText(buildBody(request), true);
            sender.send(message);
            repository.finishMail(seq, token, "SENT", now, null);
        } catch (Exception e) {
            repository.finishMail(seq, token, mail.getAttemptCount() >= 3 ? "FAILED" : "READY", now.plusMinutes(5),
                    "메일 발송 실패. SMTP 설정 및 수신 주소를 확인해 주세요.");
            log.warn("접근 허용 요청 메일 발송 실패: notificationSeq={}, type={}", seq, e.getClass().getSimpleName());
        }
    }

    String buildBody(AdminAccessRequest request) {
        String siteUrl = RequestSiteUrlResolver.normalize(request.getSiteUrl());
        String siteLink = HtmlUtils.htmlEscape(siteUrl);
        String adminLink = HtmlUtils.htmlEscape(mailLink.create(request));
        return """
                <div style="font-family:Arial,'Malgun Gothic',sans-serif;line-height:1.7;color:#1e293b">
                  <h2>관리자 페이지 접근 허용 요청 #%d</h2>
                  <p>도메인: <a href="%s">%s</a></p>
                  <p>요청 IP: %s<br>소속: %s<br>이름: %s<br>연락처: %s<br>사용기간: %s ~ %s<br>접수시각: %s</p>
                  <p>목적:</p><div style="white-space:pre-wrap">%s</div>
                  <p><a href="%s">요청 확인 및 접근 허용</a></p>
                  <p>요청 정보를 확인한 뒤 관리자 아이디와 비밀번호를 입력하여 접근을 허용해 주세요.</p>
                  <p>요청 접수 후 24시간 동안 링크를 사용할 수 있습니다.</p>
                </div>
                """.formatted(request.getSeq(), siteLink, escape(request.getSiteUrl()), escape(request.getRequestIp()),
                escape(request.getAffiliation()), escape(request.getRequesterName()), escape(request.getContact()),
                request.getStartDate(), request.getEndDate(), request.getCreatedAt(), escape(request.getPurpose()), adminLink);
    }

    private String escape(String value) { return HtmlUtils.htmlEscape(value == null ? "" : value); }
}
