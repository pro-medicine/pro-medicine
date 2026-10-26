package com.ucamp.app.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 작업(복용 알림 등)을 켠다.
 * Application.java(공통 파일)를 수정하지 않도록 별도 설정 클래스로 둔다.
 * notification.scheduler.enabled=false 이면 꺼진다. (테스트에서는 src/test/resources/config/application.yml로 끔)
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "notification.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
