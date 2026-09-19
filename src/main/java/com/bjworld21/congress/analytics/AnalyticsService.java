package com.bjworld21.congress.analytics;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class AnalyticsService {
    public static final ZoneId ZONE=ZoneId.of("Asia/Seoul");
    private final AnalyticsRepository repository;
    private final AnalyticsSnapshotReader reader;
    private final ThreadPoolExecutor refreshExecutor=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32),task->{var thread=new Thread(task,"analytics-snapshot-refresh");thread.setDaemon(true);return thread;});
    private final AnalyticsSnapshotCache<ReportKey,Map<String,Object>> cache=new AnalyticsSnapshotCache<>(Clock.systemUTC(),refreshExecutor);
    private final AnalyticsSnapshotCache<Long,List<Map<String,Object>>> countryCache=new AnalyticsSnapshotCache<>(Clock.systemUTC(),refreshExecutor);
    private final Set<Day> dirty=ConcurrentHashMap.newKeySet();
    private final Set<Day> forceRebuild=ConcurrentHashMap.newKeySet();
    private final Set<Day> backfill=ConcurrentHashMap.newKeySet();
    private final Set<Long> resetting=ConcurrentHashMap.newKeySet();
    private volatile boolean initialized;
    private final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(getClass());
    record ReportKey(long conference,LocalDate start,LocalDate end,boolean dashboard) {}
    record Day(long conference,LocalDate date) {}
    public AnalyticsService(AnalyticsRepository repository) { this(repository,new AnalyticsSnapshotReader(repository)); }
    @Autowired
    public AnalyticsService(AnalyticsRepository repository,AnalyticsSnapshotReader reader) { this.repository=repository;this.reader=reader; }
    @PreDestroy
    public void close() { refreshExecutor.shutdownNow(); }
    public void requireConference(long conference) {
        if(conference<=0 || !repository.conferenceExists(conference))
            throw new IllegalArgumentException("학회를 찾을 수 없습니다.");
    }
    public void dirty(long conference,LocalDate date) {var day=new Day(conference,date);forceRebuild.add(day);dirty.add(day);}
    public void invalidate() {cache.clear();countryCache.clear();}
    public synchronized Map<String,Long> beginTestDataReset(long conference) {
        requireConference(conference);
        if(!resetting.add(conference))throw new IllegalStateException("Analytics reset already running");
        try {
            var deleted=repository.deleteConferenceAnalytics(conference);
            dirty.removeIf(day->day.conference==conference);
            forceRebuild.removeIf(day->day.conference==conference);
            backfill.removeIf(day->day.conference==conference);
            invalidate();
            return deleted;
        } catch(RuntimeException exception) {resetting.remove(conference);throw exception;}
    }
    public synchronized void finishTestDataReset(long conference) {
        resetting.remove(conference);
        invalidate();
    }
    private void invalidate(long conference) {
        cache.markStale(key->key.conference==conference || (key.dashboard && key.conference>conference));
        countryCache.markStale(key->key==conference);
    }
    public static class AggregationPendingException extends RuntimeException {
        public AggregationPendingException() {super("기존 접속 데이터를 경량 통계로 집계 중입니다. 완료되면 자동으로 표시됩니다.");}
    }
    private void requireReady(long conference) {
        if(!initialized || backfill.stream().anyMatch(day->day.conference==conference))throw new AggregationPendingException();
    }
    public List<Map<String,Object>> mapCountries(long conference) {
        requireConference(conference);
        requireReady(conference);
        // TEMP: bypass the snapshot cache for loading-time tests. Restore the cached return after testing.
        // return countryCache.get(conference,()->reader.mapCountries(conference));
        return reader.mapCountries(conference);
    }
    @Scheduled(fixedDelay=300000,initialDelay=300000)
    public void refreshRecentDays() {
        // Today is periodic; older days are queued only until their final expiry refresh succeeds.
        repository.recentDays().forEach(row -> dirty.add(new Day(AnalyticsRepository.number(row,"conferenceSeq"),LocalDate.parse(row.get("statDate").toString()))));
    }
    @Scheduled(fixedDelay=10000,initialDelay=1000)
    public synchronized void aggregateDirtyDays() {
        if(!initialized) {
            try {
                for(var row:repository.pendingDays()) {
                    var day=new Day(AnalyticsRepository.number(row,"conferenceSeq"),LocalDate.parse(row.get("statDate").toString()));
                    backfill.add(day);forceRebuild.add(day);dirty.add(day);
                }
                refreshRecentDays();
                initialized=true;
            } catch(Exception exception) {log.error("Analytics read-model discovery failed",exception);return;}
        }
        for(var day:dirty.stream().sorted(Comparator.comparing(Day::date).thenComparingLong(Day::conference)).toList()) {
            if(resetting.contains(day.conference))continue;
            if(!dirty.remove(day))continue;
            boolean force=forceRebuild.remove(day);
            try {
                if(force)repository.rebuildDay(day.conference,day.date);else repository.refreshDay(day.conference,day.date);
                backfill.remove(day);invalidate(day.conference);
            }
            catch(Exception exception) {if(force)forceRebuild.add(day);dirty.add(day);log.error("Analytics daily aggregation failed: conference={}, date={}",day.conference,day.date,exception);}
        }
    }
    public Map<String,Object> overview(long conference,LocalDate start,LocalDate end) {
        return report(conference,start,end,false);
    }
    public AnalyticsRepository.DateRange defaultPeriod(long conference) {
        return defaultPeriod(conference,LocalDate.now(ZONE));
    }
    AnalyticsRepository.DateRange defaultPeriod(long conference,LocalDate today) {
        requireConference(conference);
        requireReady(conference);
        var recentStart=today.minusDays(29);
        var available=repository.availablePeriod(conference,today);
        if(available.isPresent() && available.get().endDate().isBefore(recentStart)) {
            var period=available.get();
            var earliest=period.endDate().minusDays(89);
            return new AnalyticsRepository.DateRange(
                    period.startDate().isBefore(earliest)?earliest:period.startDate(),period.endDate());
        }
        return new AnalyticsRepository.DateRange(recentStart,today);
    }
    public Map<String,Object> dashboard(long conference,LocalDate start,LocalDate end) {
        var result=report(conference,start,end,true);
        var countries=mapCountries(conference);
        result.put("mapCountries",countries);
        result.put("countries",countries);
        return result;
    }
    private Map<String,Object> report(long conference,LocalDate start,LocalDate end,boolean dashboard) {
        requireConference(conference);
        if(!dashboard && (start==null || end==null || end.isBefore(start) || ChronoUnit.DAYS.between(start,end)>89 || end.isAfter(LocalDate.now(ZONE))))
            throw new IllegalArgumentException("조회 기간은 오늘까지 최대 90일로 선택해 주세요.");
        requireReady(conference);
        // TEMP: bypass the snapshot cache for loading-time tests. Restore both cached lines after testing.
        // var key=new ReportKey(conference,dashboard?null:start,dashboard?null:end,dashboard);
        // var result=new LinkedHashMap<>(cache.get(key,()->reader.overview(conference,start,end,dashboard)));
        var result=new LinkedHashMap<>(reader.overview(conference,start,end,dashboard));
        if(result.get("previousConference") instanceof AnalyticsRepository.PreviousConference previous)
            requireReady(previous.conferenceSeq());
        result.put("realtime",reader.realtime(conference));
        return result;
    }
}
