package com.bjworld21.conference.service;

import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.entity.AdminAccessRequest;
import com.bjworld21.conference.security.RequestSiteUrlResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Base64;

/** Authorizes reading one emailed request; approval still requires administrator credentials. */
@Service
public class AdminAccessRequestMailLink {
    private final PersonalDataProperties personalData;
    private final Clock clock;

    public AdminAccessRequestMailLink(PersonalDataProperties personalData, Clock clock) {
        this.personalData = personalData;
        this.clock = clock;
    }

    public String create(AdminAccessRequest request) {
        long expires = request.getExpiresAt().atZone(ZoneId.of("Asia/Seoul")).toEpochSecond();
        return RequestSiteUrlResolver.normalize(request.getSiteUrl())
                + "/admin/access-request-approval?seq=" + request.getSeq()
                + "&expires=" + expires + "&signature=" + signature(request.getSeq(), expires);
    }

    public void verify(Long seq, long expires, String signature) {
        if (seq == null || seq <= 0 || signature == null || signature.length() != 43
                || !MessageDigest.isEqual(signature(seq, expires).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "유효하지 않은 요청 확인 링크입니다.");
        }
        if (expires <= clock.instant().getEpochSecond()) {
            throw new ResponseStatusException(HttpStatus.GONE, "요청 확인 링크가 만료되었습니다. 관리자 화면에서 확인해 주세요.");
        }
    }

    private String signature(Long seq, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(personalData.requireDbEncString().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] payload = ("admin-access-request-mail-view:v1:" + seq + ":" + expires).getBytes(StandardCharsets.UTF_8);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("요청 확인 링크를 생성하지 못했습니다.", exception);
        }
    }
}
