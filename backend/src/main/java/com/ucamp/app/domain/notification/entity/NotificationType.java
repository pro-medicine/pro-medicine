package com.ucamp.app.domain.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 새 종류를 추가하면 DB의 notifications CHECK 제약(ck_notif_kind)도 함께 바꿔야 한다.
// (backend/docs/sql/STEP11_notification_types.sql)
@Getter
@RequiredArgsConstructor
public enum NotificationType {
    INTAKE_REMINDER(NotificationCategory.INTAKE),       // 복용 시간 알림 (1시간 뒤 목록에서 사라짐)
    INTAKE_MISSED(NotificationCategory.INTAKE),         // 복용 놓침 알림 (복용 시간 1시간 뒤 생성, 다시 1시간 뒤 사라짐)
    FRIEND_GOAL_ACHIEVED(NotificationCategory.FRIEND),  // 친구 복용 완료 알림
    NEW_FOLLOWER(NotificationCategory.FRIEND),          // 팔로우 요청 알림
    FOLLOW_ACCEPTED(NotificationCategory.FRIEND),      // 팔로우 수락 알림
    FRIEND_NUDGE(NotificationCategory.FRIEND),          // 친구가 영양제 챙겨주기(콕 찌르기)
    GUARDIAN(NotificationCategory.FRIEND),              // 안부지킴이: 친구가 오래 접속하지 않음
    GUARDIAN_REQUESTED(NotificationCategory.FRIEND),    // 안부지킴이 신청 받음 (알림에서 수락·거절)
    GUARDIAN_ACCEPTED(NotificationCategory.FRIEND);     // 안부지킴이 신청이 수락됨

    private final NotificationCategory category;
}
