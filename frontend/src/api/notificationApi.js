import { api } from "./client";

// 알림 API (/api/v1/notifications)
// 사용자 식별: 로그인 연동 전까지 X-User-Id 헤더 사용 (백엔드 NotificationController와 동일)
// client.js 인터셉터가 응답 { success, data }에서 data만 꺼내 준다.

function userHeader(userId) {
    return { headers: { "X-User-Id": userId } };
}

/**
 * 알림 목록 (최신 50개)
 * @param category "INTAKE"(내 복용) | "FRIEND"(친구 소식) | 없으면 전체
 * @returns [{ notificationId, type, category, title, actorUserId, read, createdAt }]
 */
export function getNotifications(userId, category) {
    return api.get("/api/v1/notifications", {
        ...userHeader(userId),
        params: category ? { category } : {},
    });
}

/**
 * 안 읽은 알림 개수
 * @returns { unreadCount }
 */
export function getUnreadCount(userId) {
    return api.get("/api/v1/notifications/unread-count", userHeader(userId));
}

/**
 * 알림 하나 읽음
 * @returns 읽음 처리된 알림 1개
 */
export function markNotificationRead(userId, notificationId) {
    return api.patch(
        `/api/v1/notifications/${notificationId}/read`,
        null,
        userHeader(userId),
    );
}

/** 모두 읽음 */
export function markAllNotificationsRead(userId) {
    return api.patch("/api/v1/notifications/read-all", null, userHeader(userId));
}