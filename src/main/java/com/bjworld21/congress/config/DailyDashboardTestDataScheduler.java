package com.bjworld21.congress.config;

import com.bjworld21.congress.service.DailyDashboardTestDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 임시 대시보드 테스트 데이터 스케줄러입니다.
 * 자동 생성을 제거할 때는 이 파일만 삭제하면 스케줄 실행이 중단됩니다.
 */
@Configuration
@EnableScheduling
public class DailyDashboardTestDataScheduler {
    private static final Logger log = LoggerFactory.getLogger(DailyDashboardTestDataScheduler.class);

    private final DailyDashboardTestDataService service;

    @Value("${app.test-data.daily-conference-seq:1}")
    private long conferenceSeq;

    public DailyDashboardTestDataScheduler(DailyDashboardTestDataService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 1 0 * * *", zone = DailyDashboardTestDataService.SCHEDULE_ZONE)
    public void createDailyDashboardSamples() {
        try {
            DailyDashboardTestDataService.DailySetCreationResult result = service.createScheduledSet(conferenceSeq);
            log.info(
                    "일별 대시보드 테스트 데이터 생성 완료: 회원 {}건, 사전등록 {}건, 초록 {}건",
                    result.members().createdCount(),
                    result.preRegistrations().createdCount(),
                    result.abstracts().createdCount()
            );
        } catch (Exception exception) {
            log.error("일별 대시보드 테스트 데이터 생성 중 오류가 발생했습니다.", exception);
        }
    }
}
