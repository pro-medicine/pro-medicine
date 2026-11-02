package com.ucamp.app.domain.notification.dto;

import com.ucamp.app.domain.notification.entity.Notification;
import com.ucamp.app.domain.notification.entity.NotificationCategory;
import com.ucamp.app.domain.notification.entity.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        NotificationType type,
        NotificationCategory category,
        String title,
        Long actorUserId,
        boolean read,
        LocalDateTime createdAt,
        // 복용 알림의 알림 시각 (날짜 + 시각). 알림의 "복용 완료" 버튼이 이 시각의 일정을 체크할 때 사용
        LocalDateTime scheduledAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getNotificationId(),
                n.getNotificationType(),
                n.getNotificationType().getCategory(),
                n.getTitle(),
                n.getActorUser() == null ? null : n.getActorUser().getId(),
                n.isRead(),
                n.getCreatedAt(),
                n.getScheduledAt()
        );
    }
}
