package com.bjworld21.conference;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.mybatis.spring.annotation.MapperScan;

import java.time.Clock;
import java.time.ZoneId;

@SpringBootApplication
@MapperScan("com.bjworld21.conference.repository")
public class Bjworld21CmsConferenceApplication {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public static void main(String[] args) {
        SpringApplication.run(Bjworld21CmsConferenceApplication.class, args);
    }

    @Bean
    Clock applicationClock() {
        return Clock.system(SEOUL);
    }

}
