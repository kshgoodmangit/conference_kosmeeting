package com.bjworld21.conference.analytics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in maintenance verification: updates derived analytics ONLY for the explicit conference.
 * No server, schedulers, schema initializer, raw insert/delete, or other application beans run.
 */
@EnabledIfEnvironmentVariable(named="ANALYTICS_VERIFY_CONFERENCE",matches="[1-9][0-9]*")
class AnalyticsReadModelIntegrationTest {
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement
    @Import(AnalyticsRepository.class)
    static class Config {}

    @Test void backfillAndCompareRetainedHistory() {
        long conference=Long.parseLong(System.getenv("ANALYTICS_VERIFY_CONFERENCE"));
        new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,JdbcTemplateAutoConfiguration.class,DataSourceTransactionManagerAutoConfiguration.class))
            .withUserConfiguration(Config.class).run(context->{
                assertNull(context.getStartupFailure());
                var jdbc=context.getBean(JdbcTemplate.class);
                var repository=context.getBean(AnalyticsRepository.class);
                assertTrue(repository.conferenceExists(conference));
                var dates=jdbc.queryForList("SELECT DISTINCT DATE(occurredAt) AS statDate FROM analytics_events WHERE conferenceSeq=? ORDER BY statDate",conference);
                assertFalse(dates.isEmpty());
                int completed=0;
                for(var row:dates) {
                    var date=LocalDate.parse(row.get("statDate").toString());
                    repository.refreshDay(conference,date);
                    System.out.println("READ_MODEL_BACKFILL "+(++completed)+"/"+dates.size()+" "+date);
                }
                var transaction=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                transaction.setReadOnly(true);
                transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
                transaction.executeWithoutResult(status->{
                    var end=LocalDate.parse(dates.get(dates.size()-1).get("statDate").toString());
                    var first=LocalDate.parse(dates.get(0).get("statDate").toString());
                    verifyPeriod(jdbc,repository,conference,first,end);
                    verifyPeriod(jdbc,repository,conference,end.minusDays(29),end);
                    var empty=first.minusDays(1);
                    assertEquals(0,AnalyticsRepository.number(repository.totals(conference,empty,empty),"pageViews"));
                    assertTrue(repository.dimension(conference,empty,empty,"countryCode").isEmpty());
                    assertTrue(repository.hourly(conference,empty,empty).isEmpty());
                });
                System.out.println("READ_MODEL_VERIFIED conference="+conference+" days="+dates.size());
            });
    }

    private static void verifyPeriod(JdbcTemplate jdbc,AnalyticsRepository repository,long conference,LocalDate start,LocalDate end) {
        // Compare precisely the committed watermark represented by the read model, excluding newer live arrivals.
        String from=" FROM analytics_events e JOIN analytics_daily_summary s ON s.conferenceSeq=e.conferenceSeq AND s.statDate=DATE(e.occurredAt) WHERE e.conferenceSeq=? AND e.occurredAt>=? AND e.occurredAt<? AND e.seq<=s.sourceMaxEventSeq AND e.isBot=0 ";
        Object[] parameters={conference,start.atStartOfDay(),end.plusDays(1).atStartOfDay()};
        var expected=jdbc.queryForMap("SELECT COUNT(DISTINCT CASE WHEN eventType='PAGE_VIEW' THEN visitorIdHash END) AS visitors,COUNT(DISTINCT CASE WHEN eventType='PAGE_VIEW' THEN sessionIdHash END) AS sessions,COALESCE(SUM(eventType='PAGE_VIEW'),0) AS pageViews,COALESCE(SUM(durationSeconds),0) AS totalDurationSeconds"+from,parameters);
        var actual=repository.totals(conference,start,end);
        for(var key:List.of("visitors","sessions","pageViews","totalDurationSeconds"))assertEquals(AnalyticsRepository.number(expected,key),AnalyticsRepository.number(actual,key),start+" "+key);
        for(var column:List.of("countryCode","deviceType","sourceType","browserFamily","osFamily","pagePath")) {
            var rows=jdbc.queryForList("SELECT COALESCE(NULLIF("+column+",''),'UNKNOWN') AS dimensionKey,COUNT(DISTINCT visitorIdHash) AS visitors,COUNT(DISTINCT sessionIdHash) AS sessions,COUNT(*) AS pageViews"+from+" AND eventType='PAGE_VIEW' GROUP BY COALESCE(NULLIF("+column+",''),'UNKNOWN')",parameters);
            assertEquals(normalize(rows),normalize(repository.dimension(conference,start,end,column)),column+" "+start);
        }
        long trendViews=repository.trend(conference,start,end).stream().mapToLong(row->AnalyticsRepository.number(row,"pageViews")).sum();
        long hourViews=repository.hourly(conference,start,end).stream().mapToLong(row->AnalyticsRepository.number(row,"pageViews")).sum();
        assertEquals(AnalyticsRepository.number(expected,"pageViews"),trendViews);
        assertEquals(trendViews,hourViews);
        System.out.println("READ_MODEL_MATCH "+start+".."+end+" "+actual);
    }

    @Test void compareExpiryAndHourCellsWithoutRebuilding() {
        long conference=Long.parseLong(System.getenv("ANALYTICS_VERIFY_CONFERENCE"));
        new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,JdbcTemplateAutoConfiguration.class,DataSourceTransactionManagerAutoConfiguration.class))
            .withUserConfiguration(Config.class).run(context->{
                assertNull(context.getStartupFailure());
                var jdbc=context.getBean(JdbcTemplate.class);
                var repository=context.getBean(AnalyticsRepository.class);
                var stableDate=jdbc.queryForObject("SELECT MIN(statDate) FROM analytics_daily_summary WHERE conferenceSeq=? AND aggregationVersion=? AND statDate<?",java.sql.Date.class,conference,AnalyticsRepository.AGGREGATION_VERSION,LocalDate.now(AnalyticsService.ZONE).minusDays(1));
                assertNotNull(stableDate,"A completed historical day is required for unchanged-day verification");
                var before=jdbc.queryForMap("SELECT COUNT(*) AS n,MAX(seq) AS maxSeq FROM analytics_daily_facts WHERE conferenceSeq=? AND statDate=?",conference,stableDate);
                repository.refreshDay(conference,stableDate.toLocalDate());
                assertEquals(before,jdbc.queryForMap("SELECT COUNT(*) AS n,MAX(seq) AS maxSeq FROM analytics_daily_facts WHERE conferenceSeq=? AND statDate=?",conference,stableDate));
                var transaction=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                transaction.setReadOnly(true);
                transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
                transaction.executeWithoutResult(status->{
                    var last=jdbc.queryForObject("SELECT MAX(statDate) FROM analytics_daily_summary WHERE conferenceSeq=? AND aggregationVersion=?",java.sql.Date.class,conference,AnalyticsRepository.AGGREGATION_VERSION);
                    assertNotNull(last,"Backfill must complete before read-only verification");
                    var end=last.toLocalDate();
                    for(int days:List.of(30,60)) {
                        var start=end.minusDays(days-1);
                        String from=" FROM analytics_events e JOIN analytics_daily_summary s ON s.conferenceSeq=e.conferenceSeq AND s.statDate=DATE(e.occurredAt) WHERE e.conferenceSeq=? AND e.occurredAt>=? AND e.occurredAt<? AND e.seq<=s.sourceMaxEventSeq AND e.isBot=0 ";
                        Object[] parameters={conference,start.atStartOfDay(),end.plusDays(1).atStartOfDay()};
                        var inactive=java.time.LocalDateTime.now(AnalyticsService.ZONE).minusMinutes(30);
                        var expiryParameters=new ArrayList<Object>(Arrays.asList(parameters));
                        expiryParameters.add(inactive);
                        var bounced=jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT sessionIdHash"+from+" GROUP BY sessionIdHash HAVING SUM(eventType='PAGE_VIEW')=1 AND SUM(durationSeconds)<10 AND MAX(occurredAt)<?) b",Long.class,expiryParameters.toArray());
                        assertEquals(bounced.longValue(),AnalyticsRepository.number(repository.totals(conference,start,end),"bouncedSessions"));
                        var expectedExits=jdbc.queryForList("WITH ranked AS (SELECT pagePath,occurredAt,ROW_NUMBER() OVER(PARTITION BY sessionIdHash ORDER BY occurredAt DESC,e.seq DESC) AS rn"+from+") SELECT pagePath,COUNT(*) AS exits FROM ranked WHERE rn=1 AND occurredAt<? GROUP BY pagePath",expiryParameters.toArray());
                        assertEquals(keyed(expectedExits,"pagePath","exits"),keyed(repository.exits(conference,start,end),"pagePath","exits"));
                        var rawHours=jdbc.queryForList("SELECT CONCAT(WEEKDAY(occurredAt),':',HOUR(occurredAt)) AS slot,SUM(eventType='PAGE_VIEW') AS pageViews"+from+" GROUP BY WEEKDAY(occurredAt),HOUR(occurredAt)",parameters);
                        var actualHours=new TreeMap<String,Long>();
                        repository.hourly(conference,start,end).forEach(row->actualHours.put(row.get("dayOfWeek")+":"+row.get("hour"),AnalyticsRepository.number(row,"pageViews")));
                        assertEquals(keyed(rawHours,"slot","pageViews"),actualHours);
                        System.out.println("READ_MODEL_EXPIRY_HOURS_MATCH days="+days);
                    }
                });
            });
    }
    private static Map<String,Long> keyed(List<Map<String,Object>> rows,String key,String value) {
        var result=new TreeMap<String,Long>();
        rows.forEach(row->result.put(row.get(key).toString(),AnalyticsRepository.number(row,value)));
        return result;
    }
    private static Map<String,List<Long>> normalize(List<Map<String,Object>> rows) {
        var result=new TreeMap<String,List<Long>>();
        rows.forEach(row->result.put(row.get("dimensionKey").toString(),List.of(AnalyticsRepository.number(row,"visitors"),AnalyticsRepository.number(row,"sessions"),AnalyticsRepository.number(row,"pageViews"))));
        return result;
    }
}
