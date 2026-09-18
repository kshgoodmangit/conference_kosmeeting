package com.bjworld21.congress.analytics;

import com.bjworld21.congress.config.IpAccessExempt;
import com.bjworld21.congress.config.LicenseProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;
import java.util.LinkedHashMap;

@RestController
public class AnalyticsController {
    private final AnalyticsCollector collector;
    private final AnalyticsService service;
    private final AnalyticsTestDataService testData;
    private final LicenseProperties license;
    public AnalyticsController(AnalyticsCollector collector,AnalyticsService service,AnalyticsTestDataService testData,LicenseProperties license) {
        this.collector=collector;this.service=service;this.testData=testData;this.license=license;
    }
    @IpAccessExempt
    @GetMapping("/api/analytics/config")
    public ResponseEntity<?> config(CsrfToken csrf) {
        return ResponseEntity.ok().header("Cache-Control","no-store").body(Map.of("headerName",csrf.getHeaderName(),"token",csrf.getToken()));
    }
    @IpAccessExempt
    @PostMapping("/api/analytics/events")
    public ResponseEntity<?> event(@Valid @RequestBody AnalyticsCollector.Event event,HttpServletRequest request) {
        if("cross-site".equals(request.getHeader("Sec-Fetch-Site")))return ResponseEntity.status(403).build();
        try{return collector.collect(event,request)?ResponseEntity.noContent().build():ResponseEntity.status(429).build();}
        catch(IllegalArgumentException exception){return ResponseEntity.badRequest().body(exception.getMessage());}
    }
    @ExceptionHandler(AnalyticsService.AggregationPendingException.class)
    public ResponseEntity<String> aggregationPending(AnalyticsService.AggregationPendingException exception) {
        return ResponseEntity.status(503).header("Retry-After","10").body(exception.getMessage());
    }
    @GetMapping("/api/admin/analytics")
    public ResponseEntity<?> overview(@RequestHeader("X-Conference-Seq") long conference,
                                     @RequestParam(required=false) LocalDate startDate,
                                     @RequestParam(required=false) LocalDate endDate,
                                     @RequestParam(defaultValue="false") boolean includeMapCountries) {
        var end=endDate==null?LocalDate.now(AnalyticsService.ZONE):endDate;
        var start=startDate==null?end.minusDays(29):startDate;
        try {
            var result=new LinkedHashMap<>(service.overview(conference,start,end));
            if(includeMapCountries)result.put("mapCountries",service.mapCountries(conference));
            return ResponseEntity.ok().header("Cache-Control","no-store").body(result);
        }
        catch(IllegalArgumentException exception){return ResponseEntity.badRequest().body(exception.getMessage());}
    }
    @GetMapping("/api/admin/analytics/dashboard")
    public ResponseEntity<?> dashboard(@RequestHeader("X-Conference-Seq") long conference) {
        if(!license.isUserAnalyticsDashboardEnabled())return ResponseEntity.status(403).body("사용자 접속 현황판 라이선스가 비활성화되어 있습니다.");
        try { return ResponseEntity.ok().header("Cache-Control","no-store").body(service.dashboard(conference,null,null)); }
        catch(IllegalArgumentException exception){return ResponseEntity.badRequest().body(exception.getMessage());}
    }
    @PostMapping("/api/admin/testdata/analytics")
    public ResponseEntity<?> generate(@RequestHeader("X-Conference-Seq") long conference,
                                     @RequestParam long confirmedConferenceSeq) {
        if(conference!=confirmedConferenceSeq)return ResponseEntity.badRequest().body("선택 행사가 변경되었습니다. 삭제 대상을 다시 확인해 주세요.");
        try {service.requireConference(conference);return ResponseEntity.accepted().body(testData.start(conference));}
        catch(IllegalArgumentException exception){return ResponseEntity.badRequest().body(exception.getMessage());}
    }
    @GetMapping("/api/admin/testdata/analytics")
    public Map<String,Object> status(@RequestHeader("X-Conference-Seq") long conference) {service.requireConference(conference);return testData.status(conference);}
}
