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
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getType().getCategory(),
                n.getTitle(),
                n.getActorUserId(),
                n.isRead(),
                n.getCreatedAt()
        );
    }
}
