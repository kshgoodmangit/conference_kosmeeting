package com.bjworld21.congress.analytics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Only actual SQL work opens a transaction, including background cache refreshes. */
@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ, timeout=60)
public class AnalyticsSnapshotReader {
    private final AnalyticsRepository repository;

    public AnalyticsSnapshotReader(AnalyticsRepository repository) { this.repository=repository; }

    public List<Map<String,Object>> mapCountries(long conference) {
        return repository.allTimeCountries(conference);
    }

    public Map<String,Object> realtime(long conference) { return repository.realtime(conference); }

    public Map<String,Object> overview(long conference, LocalDate start, LocalDate end, boolean dashboard) {
        if(dashboard)return dashboard(conference);
        var result=new LinkedHashMap<String,Object>();
        result.put("startDate",start.toString());result.put("endDate",end.toString());
        result.put("timezone",AnalyticsService.ZONE.getId());result.put("generatedAt",LocalDateTime.now(AnalyticsService.ZONE).toString());
        result.put("totals",repository.totals(conference,start,end));
        long days=ChronoUnit.DAYS.between(start,end)+1;
        result.put("previous",repository.totals(conference,start.minusDays(days),start.minusDays(1)));
        var daily=new HashMap<String,Map<String,Object>>();
        repository.trend(conference,start,end).forEach(row->daily.put(row.get("date").toString(),row));
        var trend=new ArrayList<Map<String,Object>>();
        for(var date=start;!date.isAfter(end);date=date.plusDays(1))
            trend.add(daily.getOrDefault(date.toString(),Map.of("date",date.toString(),"visitors",0,"pageViews",0)));
        result.put("trend",trend);
        result.put("countries",repository.dimension(conference,start,end,"countryCode"));
        result.put("sources",repository.dimension(conference,start,end,"sourceType"));
        if(!dashboard) {
            result.put("devices",repository.dimension(conference,start,end,"deviceType"));
            result.put("browsers",repository.dimension(conference,start,end,"browserFamily"));
            result.put("operatingSystems",repository.dimension(conference,start,end,"osFamily"));
            result.put("hourly",repository.hourly(conference,start,end));
            var pages=repository.dimension(conference,start,end,"pagePath");
            var exits=new HashMap<String,Long>();
            repository.exits(conference,start,end).forEach(row->exits.put((String)row.get("pagePath"),AnalyticsRepository.number(row,"exits")));
            pages.forEach(row->row.put("exits",exits.getOrDefault(row.get("dimensionKey"),0L)));
            result.put("pages",pages);
        }
        return result;
    }

    private Map<String,Object> dashboard(long conference) {
        var result=new LinkedHashMap<String,Object>();
        result.put("timezone",AnalyticsService.ZONE.getId());
        result.put("generatedAt",LocalDateTime.now(AnalyticsService.ZONE).toString());
        result.put("totals",repository.allTimeTotals(conference,true));
        var previous=repository.previousConference(conference);
        result.put("previousConference",previous.orElse(null));
        result.put("previous",previous.map(value->repository.allTimeTotals(value.conferenceSeq(),false)).orElse(null));
        var daily=new TreeMap<LocalDate,Map<String,Object>>();
        repository.allTimeTrend(conference).forEach(row->daily.put(LocalDate.parse(row.get("date").toString()),row));
        var trend=new ArrayList<Map<String,Object>>();
        LocalDate start=daily.isEmpty()?null:daily.firstKey();
        LocalDate end=daily.isEmpty()?null:daily.lastKey();
        if(start!=null)for(var date=start;!date.isAfter(end);date=date.plusDays(1))
            trend.add(daily.getOrDefault(date,Map.of("date",date.toString(),"visitors",0,"pageViews",0)));
        result.put("startDate",start==null?null:start.toString());
        result.put("endDate",end==null?null:end.toString());
        result.put("trend",trend);
        result.put("sources",repository.allTimeSources(conference));
        // Country ranking and map share one all-time query in AnalyticsService.dashboard.
        return result;
    }
}
