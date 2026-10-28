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

/**
 * 실시간 알림 구독 (SSE). 새 알림이 생기면 onNotification이 호출된다.
 * - 브라우저 EventSource는 헤더를 붙일 수 없어서 로그인 연동 전까지 userId를 쿼리로 보낸다.
 * - 연결이 끊기면 EventSource가 자동으로 다시 연결한다.
 * @returns 구독 해제 함수 (컴포넌트가 사라질 때 호출)
 */
export function subscribeNotifications(userId, onNotification) {
    const baseUrl = import.meta.env.VITE_API_URL || "";   // client.js와 같은 기준 주소 (기본: Vite 프록시)
    const source = new EventSource(
        `${baseUrl}/api/v1/notifications/subscribe?userId=${encodeURIComponent(userId)}`,
    );
    // 재연결에 성공하면 끊겨 있는 동안 놓친 알림이 있을 수 있으니 한 번 다시 조회하게 한다. (첫 연결은 제외)
    let opened = false;
    source.onopen = () => {
        if (opened) onNotification();
        opened = true;
    };
    source.addEventListener("notification", onNotification);
    return () => source.close();
}
