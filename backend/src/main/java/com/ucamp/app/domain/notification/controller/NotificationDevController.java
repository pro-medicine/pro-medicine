package com.ucamp.app.domain.notification.controller;

import com.ucamp.app.domain.intake.dto.TodayIntakeResponse;
import com.ucamp.app.domain.intake.service.IntakeService;
import com.ucamp.app.domain.notification.entity.NotificationType;
import com.ucamp.app.domain.notification.service.NotificationService;
import com.ucamp.app.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 로컬 테스트 전용: 새 알림을 바로 만들어서 실시간(SSE) 팝업을 확인한다.
 * application.yml 에 app.dev.notification-test: true 가 있을 때만 생성된다. (기본은 꺼짐 → 운영에는 없음)
 *
 * 예) POST /api/v1/notifications/dev/test?userId=1&type=GUARDIAN
 *    type: INTAKE_REMINDER, FRIEND_GOAL_ACHIEVED, NEW_FOLLOWER, FOLLOW_ACCEPTED, GUARDIAN (기본 INTAKE_REMINDER)
 *    알림을 일으킨 사람은 docs/sql/notification-test-data.sql 의 예시 사용자(9801~9804)가 있으면 그 사람으로 넣는다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications/dev")
@ConditionalOnProperty(name = "app.dev.notification-test", havingValue = "true")
public class NotificationDevController {

    // 종류별 예시 제목과 알림을 일으킨 예시 사용자 id
    private static final Map<NotificationType, String> TITLES = Map.of(
            NotificationType.INTAKE_REMINDER, "점심 영양제 1개를 드실 시간이에요",
            NotificationType.FRIEND_GOAL_ACHIEVED, "테스트서준님이 오늘 복용을 모두 완료했어요",
            NotificationType.NEW_FOLLOWER, "테스트하늘님이 팔로우를 요청했어요",
            NotificationType.FOLLOW_ACCEPTED, "테스트유진님이 팔로우 요청을 수락했어요",
            NotificationType.GUARDIAN, "테스트엄마님이 4일째 접속하지 않았어요. 안부를 확인해보세요"
    );
    private static final Map<NotificationType, Long> ACTORS = Map.of(
            NotificationType.FRIEND_GOAL_ACHIEVED, 9801L,
            NotificationType.NEW_FOLLOWER, 9802L,
            NotificationType.FOLLOW_ACCEPTED, 9804L,
            NotificationType.GUARDIAN, 9803L
    );

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final IntakeService intakeService;

    @PostMapping("/test")
    public ResponseEntity<Map<String, String>> createTestNotification(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "INTAKE_REMINDER") NotificationType type) {
        Long actorId = ACTORS.get(type);
        if (actorId != null && !userRepository.existsById(actorId)) {
            actorId = null; // 예시 사용자를 안 넣었으면 보낸 사람 없이 만든다
        }
        String title = TITLES.get(type);
        LocalDateTime scheduledAt = null;
        if (type == NotificationType.INTAKE_REMINDER) {
            // 실제 복용 알림처럼: 오늘 아직 안 먹은 첫 알림 시각의 일정들로 만든다 → 알림의 "복용 완료" 버튼으로 체크 가능
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            List<TodayIntakeResponse> pending = intakeService.getTodayIntakes(userId, today).stream()
                    .filter(i -> !Boolean.TRUE.equals(i.getTaken()) && i.getRemindAt() != null)
                    .sorted(Comparator.comparing(TodayIntakeResponse::getRemindAt))
                    .toList();
            if (!pending.isEmpty()) {
                String remindAt = pending.get(0).getRemindAt();
                long count = pending.stream().filter(i -> remindAt.equals(i.getRemindAt())).count();
                scheduledAt = LocalDateTime.of(today, LocalTime.parse(remindAt));
                title = "%s 영양제 %d개를 드실 시간이에요".formatted(slotName(pending.get(0).getMealSlot()), count).trim();
            }
        }
        // 매번 새 알림이 되도록 dedupKey를 겹치지 않게 만든다 ("DEV:" 로 시작 → 정리할 때 한 번에 지울 수 있음)
        String dedupKey = "DEV:" + type.name() + ":" + UUID.randomUUID();
        notificationService.create(userId, type, title, actorId, null, scheduledAt, dedupKey);
        return ResponseEntity.ok(Map.of("result", "created", "type", type.name()));
    }

    private static String slotName(String mealSlot) {
        if (mealSlot == null) return "";
        return switch (mealSlot) {
            case "BREAKFAST" -> "아침";
            case "LUNCH" -> "점심";
            case "DINNER" -> "저녁";
            default -> "";
        };
    }
}
