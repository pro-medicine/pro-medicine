package com.ucamp.app.domain.notification.scheduler;

import com.ucamp.app.domain.notification.service.IntakeReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 매분 0초(한국 시간)에 복용 알림을 만든다.
 * 서버 시간대와 상관없이 항상 한국 시간 기준으로 동작하도록 cron과 현재 시각 모두 Asia/Seoul을 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntakeReminderScheduler {

    private static final String KST = "Asia/Seoul";

    private final IntakeReminderService intakeReminderService;

    @Scheduled(cron = "0 * * * * *", zone = KST)
    public void sendIntakeReminders() {
        try {
            intakeReminderService.sendReminders(LocalDateTime.now(ZoneId.of(KST)));
        } catch (Exception e) {
            // 예외가 나도 다음 분에 다시 실행되며, 원인 확인을 위해 로그를 남긴다
            log.error("복용 알림 스케줄러 실행 실패", e);
        }
    }
}
