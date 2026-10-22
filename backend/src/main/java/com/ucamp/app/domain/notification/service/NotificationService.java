package com.ucamp.app.domain.notification.service;

import com.ucamp.app.domain.notification.dto.NotificationResponse;
import com.ucamp.app.domain.notification.dto.UnreadCountResponse;
import com.ucamp.app.domain.notification.entity.Notification;
import com.ucamp.app.domain.notification.entity.NotificationCategory;
import com.ucamp.app.domain.notification.entity.NotificationType;
import com.ucamp.app.domain.notification.repository.NotificationRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
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

    // ===== 조회 / 읽음 (컨트롤러에서 사용) =====

    public List<NotificationResponse> getNotifications(Long userId, NotificationCategory category) {
        List<Notification> notifications = (category == null)
                ? notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId)
                : notificationRepository.findTop50ByUserIdAndTypeInOrderByCreatedAtDesc(userId, typesOf(category));
        return notifications.stream().map(NotificationResponse::from).toList();
    }

    public UnreadCountResponse getUnreadCount(Long userId) {
        return new UnreadCountResponse(notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
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
     * @return 새로 저장했으면 true, 이미 있어서 건너뛰었으면 false
     */
    @Transactional
    public boolean create(Long receiverId, NotificationType type, String title,
                          Long actorUserId, Long scheduleId, LocalDateTime scheduledAt, String dedupKey) {
        int inserted = notificationRepository.insertIfAbsent(
                receiverId, scheduleId, actorUserId, type.name(), title, scheduledAt, dedupKey);
        return inserted == 1;
    }

    private List<NotificationType> typesOf(NotificationCategory category) {
        return Arrays.stream(NotificationType.values())
                .filter(type -> type.getCategory() == category)
                .toList();
    }
}
