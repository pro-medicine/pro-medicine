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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ===== 조회 / 읽음 (컨트롤러에서 사용) =====

    public List<NotificationResponse> getNotifications(Long userId, NotificationCategory category) {
        List<Notification> notifications = (category == null)
                ? notificationRepository.findTop50ByUser_IdOrderByCreatedAtDesc(userId)
                : notificationRepository.findTop50ByUser_IdAndNotificationTypeInOrderByCreatedAtDesc(userId, typesOf(category));
        return notifications.stream().map(NotificationResponse::from).toList();
    }

    public UnreadCountResponse getUnreadCount(Long userId) {
        return new UnreadCountResponse(notificationRepository.countByUser_IdAndReadAtIsNull(userId));
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

