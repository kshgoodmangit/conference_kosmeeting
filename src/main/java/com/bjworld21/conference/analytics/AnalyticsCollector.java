package com.bjworld21.conference.analytics;

import com.bjworld21.conference.config.PersonalDataProperties;
import com.bjworld21.conference.publicsite.PublicApiRequest;
import com.bjworld21.conference.security.ClientIpResolver;
import com.bjworld21.conference.service.ConferenceSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AnalyticsCollector {
    public record Event(
        @NotBlank @Pattern(regexp="[a-fA-F0-9-]{36}") String eventId,
        @NotBlank @Pattern(regexp="[a-fA-F0-9-]{36}") String visitorId,
        @NotBlank @Pattern(regexp="[a-fA-F0-9-]{36}") String sessionId,
        @NotBlank @Pattern(regexp="PAGE_VIEW|HEARTBEAT|PAGE_EXIT") String eventType,
        @NotBlank @Size(max=500) String pagePath,
        @Size(max=255) String pageTitle,
        @Size(max=255) String referrerHost,
        @Size(max=100) String utmSource,
        @Size(max=100) String utmMedium,
        @Size(max=200) String utmCampaign,
        @Min(0) @Max(60) int durationSeconds,
        @NotNull Instant occurredAt) {}
    private final AnalyticsRepository repository;
    private final AnalyticsService analytics;
    private final AnalyticsCountryResolver country;
    private final ClientIpResolver ipResolver;
    private final ConferenceSettingsService conferences;
    private final PersonalDataProperties secrets;
    private final Map<String,Window> limits=new ConcurrentHashMap<>();
    private record Window(long minute,int count) {}
    public AnalyticsCollector(AnalyticsRepository repository,AnalyticsService analytics,AnalyticsCountryResolver country,
                              ClientIpResolver ipResolver,ConferenceSettingsService conferences,PersonalDataProperties secrets) {
        this.repository=repository;this.analytics=analytics;this.country=country;this.ipResolver=ipResolver;
        this.conferences=conferences;this.secrets=secrets;
    }
    public boolean collect(Event event,HttpServletRequest request) {
        String path=cleanPath(event.pagePath());
        Instant now=Instant.now();
        if(event.occurredAt().isBefore(now.minusSeconds(86400)) || event.occurredAt().isAfter(now.plusSeconds(60)))
            throw new IllegalArgumentException("유효하지 않은 이벤트 시각입니다.");
        long conference=com.bjworld21.conference.publicsite.PublicSiteContext.from(request).conferenceSeq();
        String ip=ipResolver.resolve(request);
        long minute=System.currentTimeMillis()/60000;
        if(limits.size()>10000)limits.entrySet().removeIf(e->e.getValue().minute<minute);
        if(limits.size()>10000)return false;
        String rateKey=hash("rate",ip);
        var window=limits.compute(rateKey,(key,old)->old==null || old.minute!=minute?new Window(minute,1):new Window(minute,old.count+1));
        if(window.count>600)return false;
        String ua=Optional.ofNullable(request.getHeader("User-Agent")).orElse("");
        String lower=ua.toLowerCase(Locale.ROOT);
        String device=lower.matches(".*(ipad|tablet|kindle|silk|playbook).*") || (lower.contains("android")&&!lower.contains("mobile"))?"tablet":lower.matches(".*(mobile|iphone|ipod|android).*")?"mobile":"desktop";
        String browser=lower.contains("edg/")?"Edge":lower.contains("opr/")?"Opera":lower.contains("firefox")||lower.contains("fxios")?"Firefox":lower.contains("chrome")||lower.contains("crios")?"Chrome":lower.contains("safari")?"Safari":"Other";
        String os=lower.contains("android")?"Android":lower.contains("iphone")||lower.contains("ipad")?"iOS":lower.contains("windows")?"Windows":lower.contains("macintosh")?"macOS":lower.contains("linux")?"Linux":"Other";
        boolean bot=lower.isBlank() || lower.matches(".*(bot|crawler|spider|headless|slurp|bingpreview|facebookexternalhit).*");
        LocalDateTime time=LocalDateTime.ofInstant(event.occurredAt().isAfter(now)?now:event.occurredAt(),AnalyticsService.ZONE);
        String referrer=cleanHost(event.referrerHost());
        String source=source(referrer,event.utmMedium(),event.utmSource());
        Object[] values={conference,event.eventId(),hash("visitor:"+conference,event.visitorId()),hash("session:"+conference,event.sessionId()),
                event.eventType(),path,event.pageTitle(),source,referrer,event.utmSource(),event.utmMedium(),event.utmCampaign(),
                device,browser,os,country.country(ip),event.eventType().equals("PAGE_VIEW")?0:event.durationSeconds(),bot,Timestamp.valueOf(time)};
        repository.insert(Collections.singletonList(values));
        analytics.dirty(conference,time.toLocalDate());
        return true;
    }
    public String hash(String scope,String value) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secrets.requireDbEncString().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("analytics:"+scope+":"+value).getBytes(StandardCharsets.UTF_8)));
        }catch(java.security.GeneralSecurityException exception){throw new IllegalStateException(exception);}
    }
    static String cleanPath(String input) {
        String path=URI.create(input).getPath();
        if(path==null || !input.startsWith("/") || input.startsWith("//") || path.startsWith("/admin") || path.startsWith("/api") || path.contains("\\"))
            throw new IllegalArgumentException("사용자 화면 경로만 수집할 수 있습니다.");
        return path;
    }
    static String cleanHost(String value) {
        if(value==null || value.isBlank())return null;
        String host=URI.create("https://"+value).getHost();
        return host==null?null:host.toLowerCase(Locale.ROOT);
    }
    static String source(String host,String medium,String source) {
        String m=Optional.ofNullable(medium).orElse("").toLowerCase(Locale.ROOT);
        String s=Optional.ofNullable(source).orElse("").toLowerCase(Locale.ROOT);
        if(m.matches(".*(social|social-network|social-media).*") || s.matches(".*(facebook|instagram|linkedin|twitter).*"))return "social";
        if(m.matches("organic|cpc|ppc|paidsearch"))return "search";
        if(host!=null && host.matches("(^|.*\\.)(google\\.(com|co\\.kr|co\\.jp|co\\.uk|com\\.au|ca|de|fr|co\\.in)|bing\\.com|naver\\.com|daum\\.net|yahoo\\.(com|co\\.jp))"))return "search";
        if(host!=null && host.matches("(^|.*\\.)(facebook\\.com|instagram\\.com|linkedin\\.com|t\\.co|x\\.com)"))return "social";
        return host!=null || !m.isBlank() || !s.isBlank()?"referral":"direct";
    }
}
