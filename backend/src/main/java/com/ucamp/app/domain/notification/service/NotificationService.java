package com.ucamp.app.domain.notification.service;

import com.ucamp.app.domain.intake.repository.IntakeScheduleRepository;
import com.ucamp.app.domain.notification.dto.NotificationResponse;
import com.ucamp.app.domain.notification.dto.UnreadCountResponse;
import com.ucamp.app.domain.notification.entity.Notification;
import com.ucamp.app.domain.notification.entity.NotificationCategory;
import com.ucamp.app.domain.notification.entity.NotificationType;
import com.ucamp.app.domain.notification.event.NotificationCreatedEvent;
import com.ucamp.app.domain.notification.repository.NotificationRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int LIST_SIZE = 50;
    private static final List<Long> NO_EXCLUDED_IDS = List.of(-1L);
    private static final List<NotificationType> INTAKE_TYPES =
            List.of(NotificationType.INTAKE_REMINDER, NotificationType.INTAKE_MISSED);

    // 복용 알림은 복용 시각부터 1시간 동안 보인다
    public static final Duration REMINDER_VISIBLE = Duration.ofHours(1);
    // 복용 놓침 알림은 복용 시각 1시간 뒤에 생기고, 다시 1시간 동안 보인다 (복용 시각부터 2시간)
    public static final Duration MISSED_VISIBLE = Duration.ofHours(2);

    private final NotificationRepository notificationRepository;
    private final IntakeScheduleRepository intakeScheduleRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ===== 조회 / 읽음 (컨트롤러에서 사용) =====

    public List<NotificationResponse> getNotifications(Long userId, NotificationCategory category) {
        return getNotifications(userId, category, LocalDateTime.now(KST));
    }

    /**
     * now 기준으로 보이는 알림 최신 50개.
     * - 지난 복용·놓침 알림(1시간·2시간 지남)은 빠진다.
     * - 복용 체크를 마친 시각의 복용·놓침 알림도 빠진다.
     * 두 조건 모두 조회 쿼리에서 거른 뒤 50개를 자르므로, 숨긴 알림 때문에 목록이 덜 채워지지 않는다.
     * 테스트에서 시각을 고정할 수 있도록 now를 받는 메서드를 따로 둔다.
     */
    public List<NotificationResponse> getNotifications(Long userId, NotificationCategory category, LocalDateTime now) {
        List<NotificationType> types = (category == null) ? List.of(NotificationType.values()) : typesOf(category);
        return notificationRepository.findVisible(
                        userId, types,
                        NotificationType.INTAKE_REMINDER, NotificationType.INTAKE_MISSED,
                        now.minus(REMINDER_VISIBLE), now.minus(MISSED_VISIBLE),
                        doneIntakeIds(userId, now),
                        PageRequest.of(0, LIST_SIZE))
                .stream().map(NotificationResponse::from).toList();
    }

    public UnreadCountResponse getUnreadCount(Long userId) {
        return getUnreadCount(userId, LocalDateTime.now(KST));
    }

    // 목록과 같은 조건(지난 알림·복용 끝난 알림 제외)으로 개수 제한 없이 센다
    public UnreadCountResponse getUnreadCount(Long userId, LocalDateTime now) {
        return new UnreadCountResponse(notificationRepository.countVisibleUnread(
                userId,
                NotificationType.INTAKE_REMINDER, NotificationType.INTAKE_MISSED,
                now.minus(REMINDER_VISIBLE), now.minus(MISSED_VISIBLE),
                doneIntakeIds(userId, now)));
    }

    /**
     * 지금 보이는 복용·놓침 알림 중 "그 시각의 복용을 이미 마친" 알림 id.
     * 보이는 복용·놓침 알림은 최근 2시간 것뿐이라 개수가 적어 전부(unpaged) 확인한다.
     * 쿼리의 NOT IN에 빈 목록을 넣으면 DB에 따라 오류가 나므로, 없으면 존재하지 않는 id(-1)를 넣는다.
     */
    private List<Long> doneIntakeIds(Long userId, LocalDateTime now) {
        List<Long> ids = notificationRepository.findVisible(
                        userId, INTAKE_TYPES,
                        NotificationType.INTAKE_REMINDER, NotificationType.INTAKE_MISSED,
                        now.minus(REMINDER_VISIBLE), now.minus(MISSED_VISIBLE),
                        NO_EXCLUDED_IDS,
                        Pageable.unpaged())
                .stream()
                .filter(n -> isIntakeDone(userId, n))
                .map(Notification::getNotificationId)
                .toList();
        return ids.isEmpty() ? NO_EXCLUDED_IDS : ids;
    }

    /**
     * 복용 알림·놓침 알림이 가리키는 시각(scheduled_at)의 일정을 모두 먹음·건너뜀 체크했는지.
     * 알림은 같은 분(예: 12:30:00 ~ 12:30:59)의 일정을 묶어서 만들므로 같은 범위로 확인한다.
     * 체크를 해제하면 다시 false가 되어 알림이 다시 보인다. (DB에서 지우지 않음)
     */
    private boolean isIntakeDone(Long userId, Notification n) {
        if (!INTAKE_TYPES.contains(n.getNotificationType()) || n.getScheduledAt() == null) {
            return false;
        }
        LocalTime from = n.getScheduledAt().toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        return intakeScheduleRepository.countUnchecked(
                userId, n.getScheduledAt().toLocalDate(), from, from.plusSeconds(59)) == 0;
    }

    // 같은 dedupKey의 알림이 이미 있는지
    public boolean exists(String dedupKey) {
        return notificationRepository.existsByDedupKey(dedupKey);
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByNotificationIdAndUser_Id(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead();
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, LocalDateTime.now());
    }

    // ===== 알림 생성 (스케줄러, 복용 체크, 팔로우 기능에서 사용) =====

    /**
     * 알림을 저장한다. 같은 dedupKey의 알림이 이미 있으면 저장하지 않는다.
     * 저장 후 NotificationCreatedEvent를 발행해, 트랜잭션이 커밋되면 받는 사람에게 실시간(SSE) 신호를 보낸다.
     * (중복이라 저장되지 않았어도 신호는 간다. 프론트가 다시 조회해도 개수가 그대로일 뿐이라 문제없다.)
     */
    @Transactional
    public void create(Long receiverId, NotificationType type, String title,
                       Long actorUserId, Long scheduleId, LocalDateTime scheduledAt, String dedupKey) {
        create(receiverId, type, title, actorUserId, scheduleId, scheduledAt, dedupKey, null);
    }

    /**
     * referenceId: 알림이 가리키는 대상 id (예: 안부지킴이 신청 알림 → 신청 id).
     * 알림의 버튼이 "그 알림의 대상"만 처리하도록 프론트에 함께 내려준다.
     */
    @Transactional
    public void create(Long receiverId, NotificationType type, String title,
                       Long actorUserId, Long scheduleId, LocalDateTime scheduledAt, String dedupKey,
                       Long referenceId) {
        notificationRepository.insertIfAbsent(
                receiverId, scheduleId, actorUserId, type.name(), title, scheduledAt, dedupKey, referenceId);
        eventPublisher.publishEvent(new NotificationCreatedEvent(receiverId));
    }

    private List<NotificationType> typesOf(NotificationCategory category) {
        return Arrays.stream(NotificationType.values())
                .filter(type -> type.getCategory() == category)
                .toList();
    }
}

