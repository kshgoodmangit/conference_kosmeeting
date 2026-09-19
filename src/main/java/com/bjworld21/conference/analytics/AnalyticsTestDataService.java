package com.bjworld21.conference.analytics;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Service
public class AnalyticsTestDataService {
    private final AnalyticsRepository repository;
    private final AnalyticsService analytics;
    private final AnalyticsCollector collector;
    private final Clock clock;
    private com.bjworld21.conference.service.DailyDashboardTestDataService conferenceDates;
    @Autowired
    public void setConferenceDates(com.bjworld21.conference.service.DailyDashboardTestDataService dates) { this.conferenceDates = dates; }
    private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"analytics-testdata");t.setDaemon(true);return t;});
    private final Map<Long,Map<String,Object>> jobs=new ConcurrentHashMap<>();
    private final Set<Long> running=ConcurrentHashMap.newKeySet();
    private final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(getClass());
    static final String[] PATHS={"/","/online-registration","/abstract-submission",
            "/scientific-program","/invited-speakers","/notice","/faq","/sponsor"};
    static final String[] TITLES={"학술대회 홈","사전등록","초록 제출 안내","학술 프로그램","초청연자","공지사항","자주 묻는 질문","후원 안내"};
    static final String[] COUNTRIES={"KR","KR","KR","KR","KR","KR","US","JP","SG","GB","DE","AU","CN","CA","FR","IN"};
    @Autowired
    public AnalyticsTestDataService(AnalyticsRepository repository,AnalyticsService analytics,AnalyticsCollector collector) {
        this(repository,analytics,collector,Clock.system(AnalyticsService.ZONE));
    }
    AnalyticsTestDataService(AnalyticsRepository repository,AnalyticsService analytics,AnalyticsCollector collector,Clock clock) {
        this.repository=repository;this.analytics=analytics;this.collector=collector;this.clock=clock;
    }
    public synchronized Map<String,Object> start(long conference) {
        if(running.contains(conference))return status(conference);
        analytics.requireConference(conference);
        var startedAt=LocalDateTime.now(clock.withZone(AnalyticsService.ZONE));
        LocalDate end=conferenceDates == null ? startedAt.toLocalDate() : conferenceDates.conferenceRange(conference).endDate();
        LocalDateTime cutoff = conferenceDates == null ? startedAt : end.atTime(23,59,59);
        if (conferenceDates == null && startedAt.toLocalTime().isBefore(LocalTime.of(1,0))) {
            throw new IllegalArgumentException("오늘 데이터까지 안전하게 생성하려면 서울 시간 01:00 이후에 실행해 주세요.");
        }
        // Validate hashing configuration before any existing data can be deleted.
        collector.hash("visitor:"+conference,"analytics-test-preflight");
        running.add(conference);
        var job=new ConcurrentHashMap<String,Object>();
        job.put("status","RUNNING");job.put("startDate",end.minusDays(59).toString());job.put("endDate",end.toString());
        job.put("phase","RESETTING");job.put("conferenceSeq",conference);
        job.put("completedDays",0);job.put("totalDays",60);job.put("createdEvents",0L);
        jobs.put(conference,job);
        try {executor.execute(()->generate(conference,cutoff,job));}
        catch(RejectedExecutionException exception) {
            running.remove(conference);job.put("status","FAILED");
            throw new IllegalStateException("접속 데이터 생성 작업을 시작할 수 없습니다.",exception);
        }
        return new LinkedHashMap<>(job);
    }
    public Map<String,Object> status(long conference) {
        return new LinkedHashMap<>(jobs.getOrDefault(conference,Map.of("status","IDLE")));
    }
    static int dailyPageViews(LocalDate date) {return 1000+new Random(date.toEpochDay()*7919+317).nextInt(1001);}
    static String id(String input) {return UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8)).toString();}
    void generate(long conference,LocalDateTime startedAt,Map<String,Object> job) {
        LocalDate end=startedAt.toLocalDate();
        long created=0,totalViews=0;
        boolean resetStarted=false;
        var expectedVisitors=new HashSet<String>();
        long expectedSessions=0,expectedDuration=0,expectedBounces=0;
        try {
            job.putAll(analytics.beginTestDataReset(conference));
            resetStarted=true;
            job.put("phase","GENERATING");
            for(int day=0;day<60;day++) {
                LocalDate date=end.minusDays(59-day);
                int target=dailyPageViews(date);
                var random=new Random(date.toEpochDay()*3571+conference);
                var batch=new ArrayList<Object[]>(2000);
                int page=0,session=0;
                while(page<target) {
                    int visitor=random.nextInt(12000);
                    String visitorHash=collector.hash("visitor:"+conference,id("analytics-test-v1:visitor:"+visitor));
                    String sessionHash=collector.hash("session:"+conference,id("analytics-test-v1:"+conference+":"+date+":"+session));
                    int count=Math.min(1+random.nextInt(5),target-page);
                    int deviceBucket=visitor%100;
                    String device=deviceBucket<57?"mobile":deviceBucket<93?"desktop":"tablet";
                    String os=device.equals("desktop")?(visitor%5==0?"macOS":"Windows"):visitor%3==0?"iOS":"Android";
                    String browser=os.equals("iOS")?"Safari":visitor%8==0?"Edge":visitor%9==0?"Firefox":"Chrome";
                    String country=COUNTRIES[visitor%COUNTRIES.length];
                    int sourceBucket=Math.floorMod(session+visitor,100);
                    String source=sourceBucket<46?"search":sourceBucket<73?"direct":sourceBucket<89?"referral":"social";
                    String referrer=switch(source){case "search"->"www.google.com";case "referral"->"partner.example.test";case "social"->"www.linkedin.com";default->null;};
                    int second=random.nextInt(85200);
                    if(random.nextInt(100)<75)second=8*3600+random.nextInt(14*3600);
                    if(date.equals(end)) second=(int)((long)second*Math.max(1,startedAt.toLocalTime().toSecondOfDay()-3600)/85200);
                    LocalDateTime time=date.atStartOfDay().plusSeconds(second);
                    long sessionDuration=0;
                    boolean bounced=count==1 && random.nextBoolean();
                    for(int p=0;p<count;p++,page++) {
                        int pageIndex=p==0 && random.nextInt(100)<60?0:random.nextInt(PATHS.length);
                        int duration=bounced?3:12+random.nextInt(49);
                        String base="analytics-test-v1:"+conference+":"+date+":"+page;
                        batch.add(event(conference,id(base+":view"),visitorHash,sessionHash,"PAGE_VIEW",pageIndex,source,referrer,device,browser,os,country,0,time));
                        // Incremental engagement: one heartbeat plus the final remaining seconds.
                        int heartbeat=duration>30?30:0;
                        if(heartbeat>0)batch.add(event(conference,id(base+":heartbeat"),visitorHash,sessionHash,"HEARTBEAT",pageIndex,source,referrer,device,browser,os,country,heartbeat,time.plusSeconds(heartbeat)));
                        batch.add(event(conference,id(base+":exit"),visitorHash,sessionHash,"PAGE_EXIT",pageIndex,source,referrer,device,browser,os,country,duration-heartbeat,time.plusSeconds(duration)));
                        sessionDuration+=duration;
                        time=time.plusSeconds(duration+2);
                        if(batch.size()>=2000){repository.insert(batch);created+=batch.size();batch.clear();}
                    }
                    expectedVisitors.add(visitorHash);expectedSessions++;expectedDuration+=sessionDuration;
                    if(bounced)expectedBounces++;
                    session++;
                }
                if(!batch.isEmpty()){repository.insert(batch);created+=batch.size();}
                totalViews+=target;
                repository.rebuildDay(conference,date);
                analytics.invalidate();
                job.put("completedDays",day+1);job.put("createdEvents",created);job.put("pageViews",totalViews);
            }
            job.put("expectedTotals",Map.of("visitors",expectedVisitors.size(),"sessions",expectedSessions,
                    "pageViews",totalViews,
                    "totalDurationSeconds",expectedDuration,"bouncedSessions",expectedBounces));
            job.put("status","COMPLETED");
            job.put("finishedAt",LocalDateTime.now(clock.withZone(AnalyticsService.ZONE)).toString());
        }catch(Exception exception) {
            log.error("Analytics test data generation failed: conference={}",conference,exception);
            job.put("status","FAILED");job.put("message","접속 데이터 재생성에 실패했습니다. 다시 실행하면 선택 행사의 접속 데이터를 삭제하고 처음부터 생성합니다.");
        }finally{
            if(resetStarted)analytics.finishTestDataReset(conference);
            running.remove(conference);
        }
    }
    private Object[] event(long conference,String id,String visitor,String session,String type,int page,
                           String source,String referrer,String device,String browser,String os,String country,int duration,LocalDateTime time) {
        return new Object[]{conference,id,visitor,session,type,PATHS[page],TITLES[page],source,referrer,null,null,"analytics-test-v1",
                device,browser,os,country,duration,false,Timestamp.valueOf(time)};
    }
    @PreDestroy public void close(){executor.shutdown();}
}
