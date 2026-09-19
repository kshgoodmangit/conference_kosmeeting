package com.bjworld21.conference.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AbstractSimilarityAsyncConfig {

    @Bean(name = "abstractSimilarityTaskExecutor")
    public Executor abstractSimilarityTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("abstract-similarity-");
        executor.initialize();
        return executor;
    }
}
