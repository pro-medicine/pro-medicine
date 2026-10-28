package com.ucamp.app.domain.notification.scheduler;

import com.ucamp.app.domain.notification.service.NotificationSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 30초마다 모든 SSE 연결에 하트비트를 보낸다.
 * - 오래 조용한 연결을 중간 프록시가 끊지 않게 하고,
 * - 이미 닫힌 연결(브라우저 탭 종료)은 보내다 실패하면서 보관소에서 정리된다.
 * SchedulingConfig(notification.scheduler.enabled)가 꺼져 있으면 실행되지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSseHeartbeatScheduler {

    private final NotificationSseService notificationSseService;

    @Scheduled(fixedRate = 30_000)
    public void sendHeartbeat() {
        try {
            notificationSseService.sendHeartbeat();
        } catch (Exception e) {
            log.error("SSE 하트비트 전송 실패", e);
        }
    }
}
