package com.ucamp.app.perf;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

// 실제 푸시 대신 로그로 남기는 발송기 (부하 시뮬레이션용). 사용자 ID 대신 건수만 센다
@Slf4j
public class LoggingNotificationSender implements NotificationSpikeSimulator.NotificationSender {
    private final AtomicInteger sent = new AtomicInteger();

    @Override
    public void send(NotificationSpikeSimulator.PlannedNotification notification) {
        int n = sent.incrementAndGet();
        if (log.isDebugEnabled()) {
            log.debug("notification planned #{} type={} at={}", n, notification.type(), notification.scheduledAt());
        }
    }

    public int sentCount() {
        return sent.get();
    }
}
