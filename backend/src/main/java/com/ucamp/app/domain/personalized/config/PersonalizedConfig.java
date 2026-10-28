package com.ucamp.app.domain.personalized.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(PersonalizedProperties.class)
public class PersonalizedConfig {
    public static final String EXECUTOR = "pqExecutor";

    /**
     * 질문 미리 만들기·이중 요청용 스레드. LLM 대기(수 초)만 하는 작업이라 DB 트랜잭션은 잡지 않는다.
     * 가득 차면 거절(RejectedExecutionException) → 호출하는 쪽이 미리 만들기를 건너뛰거나 바로 호출한다
     */
    @Bean(name = EXECUTOR)
    public ThreadPoolTaskExecutor pqExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("pq-");
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(100);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
