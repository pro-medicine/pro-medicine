package com.ucamp.app.domain.notification.service;

import com.ucamp.app.domain.intake.entity.IntakeSchedule;
import com.ucamp.app.domain.intake.repository.IntakeScheduleRepository;
import com.ucamp.app.domain.notification.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 복용 시간 알림(INTAKE_REMINDER)을 만든다.
 * 같은 사용자·같은 알림 시각의 일정은 알림 1개로 묶고, dedup_key로 중복 생성을 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntakeReminderService {

    // 서버가 잠깐 멈춰도 놓친 알림을 보낼 수 있도록 최근 몇 분까지 함께 조회한다 (중복은 dedup_key가 막음)
    static final int LOOKBACK_MINUTES = 5;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final IntakeScheduleRepository intakeScheduleRepository;
    private final NotificationService notificationService;

    /**
     * now 기준으로 보낼 복용 알림을 만든다.
     * 테스트에서 원하는 시각을 넣을 수 있도록 현재 시각을 매개변수로 받는다.
     *
     * 트랜잭션을 걸지 않는 이유: 알림 하나 저장이 실패해도 다른 사람의 알림은 저장되도록,
     * NotificationService.create()가 알림마다 따로 트랜잭션을 연다.
     */
    public void sendReminders(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalTime to = now.toLocalTime();
        LocalTime from = rangeStart(to);

        List<IntakeSchedule> targets = intakeScheduleRepository.findReminderTargets(today, from, to);

        Map<ReminderKey, List<IntakeSchedule>> groups = targets.stream()
                .collect(Collectors.groupingBy(
                        schedule -> new ReminderKey(
                                schedule.getUserProduct().getUser().getId(),
                                schedule.getRemindAt()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        groups.forEach((key, schedules) -> {
            try {
                notificationService.create(
                        key.userId(),
                        NotificationType.INTAKE_REMINDER,
                        title(schedules),
                        null,   // 알림을 일으킨 다른 사용자 없음
                        null,   // 여러 일정을 묶으므로 특정 일정 id를 넣지 않음
                        LocalDateTime.of(today, key.remindAt()),
                        dedupKey(key.userId(), today, key.remindAt()));
            } catch (Exception e) {
                log.error("복용 알림 생성 실패 userId={}, remindAt={}", key.userId(), key.remindAt(), e);
            }
        });

        if (!groups.isEmpty()) {
            log.info("복용 알림 처리 {}건 (기준 {} {}~{})", groups.size(), today, from, to);
        }
    }

    /**
     * 조회 시작 시각. 자정 직후(예: 00:02)에는 전날로 넘어가지 않도록 00:00부터 조회한다.
     */
    static LocalTime rangeStart(LocalTime to) {
        LocalTime from = to.minusMinutes(LOOKBACK_MINUTES);
        return from.isAfter(to) ? LocalTime.MIDNIGHT : from;
    }

    /**
     * 예: "점심 영양제 2개를 드실 시간이에요"
     */
    static String title(List<IntakeSchedule> schedules) {
        String slot = mealSlotName(schedules.get(0).getMealSlot());
        return "%s 영양제 %d개를 드실 시간이에요".formatted(slot, schedules.size()).trim();
    }

    /**
     * 예: "INTAKE:1:2026-10-22:12:30"
     */
    static String dedupKey(Long userId, LocalDate date, LocalTime remindAt) {
        return "INTAKE:%d:%s:%s".formatted(userId, date, remindAt.format(TIME_FORMAT));
    }

    private static String mealSlotName(String mealSlot) {
        if (mealSlot == null) {
            return "";
        }
        return switch (mealSlot) {
            case "BREAKFAST" -> "아침";
            case "LUNCH" -> "점심";
            case "DINNER" -> "저녁";
            default -> "";
        };
    }

    private record ReminderKey(Long userId, LocalTime remindAt) {
    }
}
