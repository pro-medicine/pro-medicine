package com.ucamp.app.domain.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    INTAKE_REMINDER(NotificationCategory.INTAKE),       // 복용 시간 알림
    FRIEND_GOAL_ACHIEVED(NotificationCategory.FRIEND),  // 친구 복용 완료 알림
    NEW_FOLLOWER(NotificationCategory.FRIEND);          // 팔로우 알림

    private final NotificationCategory category;
}
