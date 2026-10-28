package com.ucamp.app.domain.guardian.scheduler;

import com.ucamp.app.domain.guardian.entity.GuardianSetting;
import com.ucamp.app.domain.guardian.repository.GuardianSettingRepository;
import com.ucamp.app.domain.notification.entity.NotificationType;
import com.ucamp.app.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class GuardianScheduler {
    private static final String KST = "Asia/Seoul";
    private final GuardianSettingRepository guardianSettingRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 9 * * *", zone = KST)
    @Transactional
    public void checkInactiveUsers() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        for (GuardianSetting g: guardianSettingRepository.findAllActiveWithUsers()) {
            LocalDateTime lastActive = g.getUser().getLastActiveAt();
            if (!lastActive.isBefore(now.minusDays(g.getInactivityDays()))) continue;
            if (g.getLastNotifiedAt() != null && g.getLastNotifiedAt().isAfter(lastActive)) continue;
            long days = ChronoUnit.DAYS.between(lastActive.toLocalDate(), now.toLocalDate());
            String title = g.getUser().getNickname() + "님이 " + days + "일째 접속하지 않았어요. 안부를 확인해보세요";
            String dedupKey = "GUARDIAN: " + g.getUser().getId() + ":" + g.getGuardianSettingId() + ":" + now.toLocalDate().format(DateTimeFormatter.ISO_DATE);
            notificationService.create(
                    g.getGuardianUser().getId(),
                    NotificationType.GUARDIAN,
                    title,
                    g.getUser().getId(),
                    null, null, dedupKey
            );
            g.markNotified();
            log.info("guardian alert: setting={}, days={}", g.getGuardianSettingId(), days);
        }
    }
}