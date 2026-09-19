package com.bjworld21.conference.analytics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class AnalyticsRepository {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private static final String FILTER = " conferenceSeq=:conference AND occurredAt>=:start AND occurredAt<:end AND isBot=0 ";
    private static final String FACT_FILTER = " conferenceSeq=:conference AND statDate>=:startDate AND statDate<=:endDate ";
    public static final int AGGREGATION_VERSION = 1;
    public static final String INSERT = """
            INSERT INTO analytics_events
            (conferenceSeq,eventId,visitorIdHash,sessionIdHash,eventType,pagePath,pageTitle,
             sourceType,referrerHost,utmSource,utmMedium,utmCampaign,deviceType,browserFamily,
             osFamily,countryCode,durationSeconds,isBot,occurredAt)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE eventId=eventId
            """;

    public AnalyticsRepository(JdbcTemplate jdbc) {
        this.jdbc=jdbc;
        this.named=new NamedParameterJdbcTemplate(jdbc);
    }

    public void insert(List<Object[]> events) {
        // Explicit multi-row inserts avoid one remote DB round trip per event.
        for (int offset=0; offset<events.size(); offset+=500) {
            var chunk=events.subList(offset,Math.min(offset+500,events.size()));
            String values=String.join(",",Collections.nCopies(chunk.size(),"(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"));
            String sql=INSERT.replace("(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",values);
            jdbc.update(sql,chunk.stream().flatMap(Arrays::stream).toArray());
        }
    }
    public boolean conferenceExists(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM conference_settings WHERE seq=?)", Boolean.class,id));
    }
    public record DateRange(LocalDate startDate, LocalDate endDate) {}

    public Optional<DateRange> availablePeriod(long conference, LocalDate today) {
        return jdbc.query("""
                SELECT MIN(statDate) AS startDate,MAX(statDate) AS endDate
                FROM analytics_daily_summary
                WHERE conferenceSeq=? AND statDate<=? AND pageViewCount>0
                HAVING MIN(statDate) IS NOT NULL
                """, (rs,row)->new DateRange(rs.getDate("startDate").toLocalDate(),
                        rs.getDate("endDate").toLocalDate()),conference,today).stream().findFirst();
    }
    @Transactional
    public Map<String,Long> deleteConferenceAnalytics(long conference) {
        if(conference<=0)throw new IllegalArgumentException("Invalid conference");
        long dimensions=jdbc.update("DELETE FROM analytics_daily_dimensions WHERE conferenceSeq=?",conference);
        long facts=jdbc.update("DELETE FROM analytics_daily_facts WHERE conferenceSeq=?",conference);
        long summaries=jdbc.update("DELETE FROM analytics_daily_summary WHERE conferenceSeq=?",conference);
        long events=jdbc.update("DELETE FROM analytics_events WHERE conferenceSeq=?",conference);
        return Map.of("deletedEvents",events,"deletedFacts",facts,"deletedSummaries",summaries,"deletedDimensions",dimensions);
    }
    public record PreviousConference(long conferenceSeq, String eventName) {}

    public Optional<PreviousConference> previousConference(long conference) {
        return jdbc.query("""
                SELECT seq,eventName FROM conference_settings
                WHERE seq<? ORDER BY seq DESC LIMIT 1
                """, (rs,row)->new PreviousConference(rs.getLong("seq"),rs.getString("eventName")),conference)
                .stream().findFirst();
    }
    private Map<String,Object> params(long conference, LocalDate start, LocalDate end) {
        return Map.of("conference",conference,"start",Timestamp.valueOf(start.atStartOfDay()),"end",Timestamp.valueOf(end.plusDays(1).atStartOfDay()),"startDate",start,"endDate",end);
    }
    public List<Map<String,Object>> pendingDays() {
        // Once per process startup: also recovers old imports and interrupted transactions.
        return jdbc.queryForList("""
                SELECT e.conferenceSeq,e.statDate FROM (
                    SELECT conferenceSeq,DATE(occurredAt) AS statDate,MAX(seq) AS maxSeq
                    FROM analytics_events GROUP BY conferenceSeq,DATE(occurredAt)
                ) e LEFT JOIN analytics_daily_summary s
                  ON s.conferenceSeq=e.conferenceSeq AND s.statDate=e.statDate
                WHERE s.seq IS NULL OR s.aggregationVersion<>? OR s.sourceMaxEventSeq<e.maxSeq
                ORDER BY e.statDate,e.conferenceSeq
                """,AGGREGATION_VERSION);
    }
    public List<Map<String,Object>> recentDays() {
        return recentDays(LocalDateTime.now(AnalyticsService.ZONE));
    }
    List<Map<String,Object>> recentDays(LocalDateTime now) {
        var today=now.toLocalDate();
        var days=new ArrayList<>(jdbc.queryForList("SELECT DISTINCT conferenceSeq,DATE(occurredAt) AS statDate FROM analytics_events WHERE occurredAt>=? AND occurredAt<?",
                today.atStartOfDay(),today.plusDays(1).atStartOfDay()));
        // Summaries are small. Keep unfinished older days recoverable even after prolonged downtime.
        var unfinished=jdbc.queryForList("""
                SELECT conferenceSeq,statDate,updatedAt FROM analytics_daily_summary
                WHERE statDate<? AND updatedAt<TIMESTAMPADD(MINUTE,30,TIMESTAMPADD(DAY,1,statDate))
                """,today);
        for(var row:unfinished) {
            var date=LocalDate.parse(row.get("statDate").toString());
            if(!now.isBefore(date.plusDays(1).atTime(0,30)))days.add(row);
        }
        return days;
    }
    public Map<String,Object> totals(long conference,LocalDate start,LocalDate end) {
        return totals(FACT_FILTER,params(conference,start,end),true," FORCE INDEX (uk_analytics_daily_fact)");
    }
    public Map<String,Object> allTimeTotals(long conference,boolean includeBouncedSessions) {
        return totals(" conferenceSeq=:conference ",Map.of("conference",conference),includeBouncedSessions,"");
    }
    private Map<String,Object> totals(String filter,Map<String,Object> p,boolean includeBouncedSessions,String bounceIndexHint) {
        var totals = new LinkedHashMap<>(named.queryForMap("""
                SELECT COUNT(DISTINCT CASE WHEN pageViewCount>0 THEN visitorIdHash END) AS visitors,
                       COUNT(DISTINCT CASE WHEN pageViewCount>0 THEN sessionIdHash END) AS sessions,
                       COALESCE(SUM(pageViewCount),0) AS pageViews,
                       COALESCE(SUM(totalDurationSeconds),0) AS totalDurationSeconds
                FROM analytics_daily_facts WHERE """+filter,p));
        if(!includeBouncedSessions)return totals;
        // Bounded reports retain the date-first index. All-time reports can use a covering session index.
        totals.put("bouncedSessions",named.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT sessionIdHash FROM analytics_daily_facts """+bounceIndexHint+" WHERE "+filter+"""
                    GROUP BY sessionIdHash
                    HAVING SUM(pageViewCount)=1 AND SUM(totalDurationSeconds)<10
                       AND MAX(lastOccurredAt)<:inactive
                ) b
                """, with(p,"inactive",Timestamp.valueOf(LocalDateTime.now(AnalyticsService.ZONE).minusMinutes(30))),Long.class));
        return totals;
    }
    public List<Map<String,Object>> trend(long conference,LocalDate start,LocalDate end) {
        return named.queryForList("""
                SELECT statDate AS date,visitorCount AS visitors,pageViewCount AS pageViews
                FROM analytics_daily_summary WHERE """+FACT_FILTER+"""
                 ORDER BY statDate
                """,params(conference,start,end));
    }
    public List<Map<String,Object>> allTimeTrend(long conference) {
        return jdbc.queryForList("""
                SELECT statDate AS date,visitorCount AS visitors,pageViewCount AS pageViews
                FROM analytics_daily_summary WHERE conferenceSeq=? ORDER BY statDate
                """,conference);
    }
    public List<Map<String,Object>> dimension(long conference,LocalDate start,LocalDate end,String column) {
        return dimension(FACT_FILTER,params(conference,start,end),column);
    }
    public List<Map<String,Object>> allTimeSources(long conference) {
        // The dashboard displays session proportions; visitor DISTINCT is not used here.
        return jdbc.queryForList("""
                SELECT COALESCE(NULLIF(sourceType,''),'UNKNOWN') AS dimensionKey,
                       COUNT(DISTINCT sessionIdHash) AS sessions,SUM(pageViewCount) AS pageViews
                FROM analytics_daily_facts WHERE conferenceSeq=? AND pageViewCount>0
                GROUP BY COALESCE(NULLIF(sourceType,''),'UNKNOWN') ORDER BY pageViews DESC,dimensionKey
                """,conference);
    }
    private List<Map<String,Object>> dimension(String filter,Map<String,Object> p,String column) {
        if (!Set.of("countryCode","deviceType","sourceType","browserFamily","osFamily","pagePath").contains(column))
            throw new IllegalArgumentException("지원하지 않는 통계입니다.");
        return named.queryForList("""
                SELECT COALESCE(NULLIF(%s,''),'UNKNOWN') AS dimensionKey,
                       COUNT(DISTINCT visitorIdHash) AS visitors,
                       COUNT(DISTINCT sessionIdHash) AS sessions,SUM(pageViewCount) AS pageViews,
                       %s AS pageTitle
                FROM analytics_daily_facts WHERE %s AND pageViewCount>0
                GROUP BY COALESCE(NULLIF(%s,''),'UNKNOWN') ORDER BY pageViews DESC, dimensionKey
                """.formatted(column,column.equals("pagePath")?"MAX(pageTitle)":"NULL",filter,column),p);
    }
    public List<Map<String,Object>> allTimeCountries(long conference) {
        // The map uses retained history for this tenant, independently of the report date range.
        return jdbc.queryForList("""
                SELECT COALESCE(NULLIF(UPPER(TRIM(countryCode)),''),'UNKNOWN') AS dimensionKey,
                       COUNT(DISTINCT visitorIdHash) AS visitors,
                       COUNT(DISTINCT sessionIdHash) AS sessions,SUM(pageViewCount) AS pageViews
                FROM analytics_daily_facts
                WHERE conferenceSeq=? AND pageViewCount>0
                GROUP BY COALESCE(NULLIF(UPPER(TRIM(countryCode)),''),'UNKNOWN')
                ORDER BY visitors DESC,dimensionKey
                """,conference);
    }
    public List<Map<String,Object>> hourly(long conference,LocalDate start,LocalDate end) {
        return named.queryForList("""
                SELECT WEEKDAY(statDate) AS dayOfWeek,CAST(dimensionKey AS UNSIGNED) AS hour,SUM(pageViewCount) AS pageViews
                FROM analytics_daily_dimensions WHERE """+FACT_FILTER+"""
                 AND dimensionType='HOUR' GROUP BY WEEKDAY(statDate),CAST(dimensionKey AS UNSIGNED)
                ORDER BY dayOfWeek,hour
                """,params(conference,start,end));
    }
    public List<Map<String,Object>> exits(long conference,LocalDate start,LocalDate end) {
        return named.queryForList("""
                WITH ranked AS (
                    SELECT pagePath,lastOccurredAt,
                      ROW_NUMBER() OVER(PARTITION BY sessionIdHash ORDER BY lastEventOrder DESC) AS rn
                    FROM analytics_daily_facts WHERE """+FACT_FILTER+"""
                )
                SELECT pagePath,COUNT(*) AS exits FROM ranked
                WHERE rn=1 AND lastOccurredAt<:inactive GROUP BY pagePath
                """,with(params(conference,start,end),"inactive",Timestamp.valueOf(LocalDateTime.now(AnalyticsService.ZONE).minusMinutes(30))));
    }
    public Map<String,Object> realtime(long conference) {
        var now=LocalDateTime.now(AnalyticsService.ZONE);
        var p=Map.<String,Object>of("conference",conference,"start",Timestamp.valueOf(now.minusMinutes(5)),
                "end",Timestamp.valueOf(now.plusSeconds(1)),"previous",Timestamp.valueOf(now.minusMinutes(10)));
        var pages=named.queryForList("""
                WITH active AS (
                    SELECT visitorIdHash,pagePath,pageTitle,eventType,
                      ROW_NUMBER() OVER(PARTITION BY visitorIdHash ORDER BY occurredAt DESC,seq DESC) AS rn
                    FROM analytics_events WHERE """+FILTER+"""
                )
                SELECT pagePath AS path,MAX(pageTitle) AS title,COUNT(*) AS visitors
                FROM active WHERE rn=1 AND eventType<>'PAGE_EXIT'
                GROUP BY pagePath ORDER BY visitors DESC,path
                """,p);
        long total=pages.stream().mapToLong(v->number(v,"visitors")).sum();
        long previous=named.queryForObject("""
                WITH previous_active AS (
                    SELECT eventType,ROW_NUMBER() OVER(PARTITION BY visitorIdHash ORDER BY occurredAt DESC,seq DESC) AS rn
                    FROM analytics_events WHERE conferenceSeq=:conference AND occurredAt>=:previous AND occurredAt<:start AND isBot=0
                ) SELECT COUNT(*) FROM previous_active WHERE rn=1 AND eventType<>'PAGE_EXIT'
                """,p,Long.class);
        return Map.of("visitors",total,"change",total-previous,"pages",pages,"asOf",now.toString());
    }

    @Transactional
    public void rebuildDay(long conference, LocalDate date) {
        aggregateDay(conference,date,true);
    }
    @Transactional
    public void refreshDay(long conference, LocalDate date) {
        aggregateDay(conference,date,false);
    }
    private void aggregateDay(long conference, LocalDate date,boolean force) {
        // Serialize writers for this day. Facts, summary, dimensions and watermark commit together.
        jdbc.update("INSERT INTO analytics_daily_summary (conferenceSeq,statDate) VALUES (?,?) ON DUPLICATE KEY UPDATE seq=seq",conference,date);
        var state=jdbc.queryForMap("SELECT sourceMaxEventSeq,aggregationVersion FROM analytics_daily_summary WHERE conferenceSeq=? AND statDate=? FOR UPDATE",conference,date);
        // Record the start, not the end: a job crossing 00:30 must not prematurely finalize yesterday.
        var aggregationStartedAt=LocalDateTime.now(AnalyticsService.ZONE);
        long watermark=jdbc.queryForObject("SELECT COALESCE(MAX(seq),0) FROM analytics_events WHERE conferenceSeq=? AND occurredAt>=? AND occurredAt<?",Long.class,conference,date.atStartOfDay(),date.plusDays(1).atStartOfDay());
        boolean changed=force || number(state,"sourceMaxEventSeq")!=watermark || number(state,"aggregationVersion")!=AGGREGATION_VERSION;
        var rawParams=with(params(conference,date,date),"watermark",watermark);
        if(changed) {
            jdbc.update("DELETE FROM analytics_daily_facts WHERE conferenceSeq=? AND statDate=?",conference,date);
            named.update("""
                    INSERT INTO analytics_daily_facts
                     (conferenceSeq,statDate,factKey,visitorIdHash,sessionIdHash,pagePath,pageTitle,
                      sourceType,deviceType,browserFamily,osFamily,countryCode,pageViewCount,
                      totalDurationSeconds,lastOccurredAt,lastEventOrder)
                    SELECT :conference,:startDate,
                      SHA2(JSON_ARRAY(visitorIdHash,sessionIdHash,pagePath,sourceType,deviceType,browserFamily,osFamily,countryCode),256) AS groupingKey,
                      MIN(visitorIdHash),MIN(sessionIdHash),MIN(pagePath),
                      MAX(CASE WHEN eventType='PAGE_VIEW' THEN pageTitle END),
                      MIN(sourceType),MIN(deviceType),MIN(browserFamily),MIN(osFamily),MIN(countryCode),
                      SUM(eventType='PAGE_VIEW'),COALESCE(SUM(durationSeconds),0),MAX(occurredAt),
                      MAX(CONCAT(DATE_FORMAT(occurredAt,'%Y%m%d%H%i%s'),LPAD(seq,20,'0')))
                    FROM analytics_events WHERE """+FILTER+"""
                     AND seq<=:watermark GROUP BY groupingKey
                    """,rawParams);
        }
        var totals=totals(conference,date,date);
        jdbc.update("""
                INSERT INTO analytics_daily_summary
                 (conferenceSeq,statDate,visitorCount,sessionCount,pageViewCount,totalDurationSeconds,bouncedSessionCount,sourceMaxEventSeq,aggregationVersion,updatedAt)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE visitorCount=VALUES(visitorCount),sessionCount=VALUES(sessionCount),
                 pageViewCount=VALUES(pageViewCount),totalDurationSeconds=VALUES(totalDurationSeconds),
                 bouncedSessionCount=VALUES(bouncedSessionCount),sourceMaxEventSeq=VALUES(sourceMaxEventSeq),aggregationVersion=VALUES(aggregationVersion),updatedAt=VALUES(updatedAt)
                """,conference,date,number(totals,"visitors"),number(totals,"sessions"),number(totals,"pageViews"),
                number(totals,"totalDurationSeconds"),number(totals,"bouncedSessions"),watermark,AGGREGATION_VERSION,Timestamp.valueOf(aggregationStartedAt));
        if(!changed) {
            // Session expiry changes without new events: refresh only the derived exit counts.
            jdbc.update("UPDATE analytics_daily_dimensions SET exitCount=0 WHERE conferenceSeq=? AND statDate=? AND dimensionType='PAGE'",conference,date);
            for(var row:exits(conference,date,date))jdbc.update("UPDATE analytics_daily_dimensions SET exitCount=? WHERE conferenceSeq=? AND statDate=? AND dimensionType='PAGE' AND dimensionKey=?",number(row,"exits"),conference,date,row.get("pagePath"));
            return;
        }
        // Derived data only; raw events are never deleted by aggregation.
        jdbc.update("DELETE FROM analytics_daily_dimensions WHERE conferenceSeq=? AND statDate=?",conference,date);
        var dimensions=Map.of("COUNTRY","countryCode","DEVICE","deviceType","SOURCE","sourceType",
                "PAGE","pagePath","BROWSER","browserFamily","OS","osFamily","HOUR","HOUR(occurredAt)");
        var exitCounts=new HashMap<String,Long>();
        exits(conference,date,date).forEach(row->exitCounts.put((String)row.get("pagePath"),number(row,"exits")));
        for (var dimension:dimensions.entrySet()) {
            boolean hour=dimension.getKey().equals("HOUR");
            String expression="COALESCE(NULLIF(CAST("+dimension.getValue()+" AS CHAR),''),'UNKNOWN')";
            var rows=named.queryForList("""
                    SELECT %s AS dimensionKey,
                      COUNT(DISTINCT CASE WHEN %s THEN visitorIdHash END) AS visitors,
                      COUNT(DISTINCT CASE WHEN %s THEN sessionIdHash END) AS sessions,
                      SUM(%s) AS pageViews,SUM(%s) AS duration
                    FROM %s WHERE %s GROUP BY %s
                    """.formatted(expression,hour?"eventType='PAGE_VIEW'":"pageViewCount>0",hour?"eventType='PAGE_VIEW'":"pageViewCount>0",
                      hour?"eventType='PAGE_VIEW'":"pageViewCount",hour?"durationSeconds":"totalDurationSeconds",
                      hour?"analytics_events":"analytics_daily_facts",hour?FILTER+" AND seq<=:watermark":FACT_FILTER,expression),rawParams);
            var batch=new ArrayList<Object[]>();
            for(var row:rows) batch.add(new Object[]{conference,date,dimension.getKey(),row.get("dimensionKey"),
                    number(row,"visitors"),number(row,"sessions"),number(row,"pageViews"),
                    dimension.getKey().equals("PAGE")?exitCounts.getOrDefault(row.get("dimensionKey"),0L):0,
                    number(row,"duration")});
            if(!batch.isEmpty()) jdbc.batchUpdate("""
                    INSERT INTO analytics_daily_dimensions
                     (conferenceSeq,statDate,dimensionType,dimensionKey,visitorCount,sessionCount,pageViewCount,exitCount,totalDurationSeconds)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    """,batch);
        }
    }
    public long testCount(long conference,LocalDate date) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM analytics_events WHERE conferenceSeq=? AND occurredAt>=? AND occurredAt<?
                 AND utmCampaign='analytics-test-v1'
                """,Long.class,conference,date.atStartOfDay(),date.plusDays(1).atStartOfDay());
    }
    public static long number(Map<String,Object> row,String key) {
        Object value=row.get(key); return value instanceof Number n?n.longValue():0;
    }
    private static Map<String,Object> with(Map<String,Object> values,String key,Object value) {
        var copy=new HashMap<>(values);copy.put(key,value);return copy;
    }
}
