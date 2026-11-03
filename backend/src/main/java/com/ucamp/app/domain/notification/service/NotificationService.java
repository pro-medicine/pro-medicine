package com.ucamp.app.domain.notification.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int LIST_SIZE = 50;

    // 복용 알림은 복용 시각부터 1시간 동안 보인다
    public static final Duration REMINDER_VISIBLE = Duration.ofHours(1);
    // 복용 놓침 알림은 복용 시각 1시간 뒤에 생기고, 다시 1시간 동안 보인다 (복용 시각부터 2시간)
    public static final Duration MISSED_VISIBLE = Duration.ofHours(2);

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ===== 조회 / 읽음 (컨트롤러에서 사용) =====

    public List<NotificationResponse> getNotifications(Long userId, NotificationCategory category) {
        return getNotifications(userId, category, LocalDateTime.now(KST));
    }

    /**
     * now 기준으로 보이는 알림 최신 50개. (지난 복용·놓침 알림은 빠짐)
     * 테스트에서 시각을 고정할 수 있도록 now를 받는 메서드를 따로 둔다.
     */
    public List<NotificationResponse> getNotifications(Long userId, NotificationCategory category, LocalDateTime now) {
        List<NotificationType> types = (category == null) ? List.of(NotificationType.values()) : typesOf(category);
        return notificationRepository.findVisible(
                        userId, types,
                        NotificationType.INTAKE_REMINDER, NotificationType.INTAKE_MISSED,
                        now.minus(REMINDER_VISIBLE), now.minus(MISSED_VISIBLE),
                        PageRequest.of(0, LIST_SIZE))
                .stream().map(NotificationResponse::from).toList();
    }

    public UnreadCountResponse getUnreadCount(Long userId) {
        return getUnreadCount(userId, LocalDateTime.now(KST));
    }

    public UnreadCountResponse getUnreadCount(Long userId, LocalDateTime now) {
        return new UnreadCountResponse(notificationRepository.countVisibleUnread(
                userId,
                NotificationType.INTAKE_REMINDER, NotificationType.INTAKE_MISSED,
                now.minus(REMINDER_VISIBLE), now.minus(MISSED_VISIBLE)));
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
        notificationRepository.insertIfAbsent(
                receiverId, scheduleId, actorUserId, type.name(), title, scheduledAt, dedupKey);
        eventPublisher.publishEvent(new NotificationCreatedEvent(receiverId));
    }

    private List<NotificationType> typesOf(NotificationCategory category) {
        return Arrays.stream(NotificationType.values())
                .filter(type -> type.getCategory() == category)
                .toList();
    }
}

