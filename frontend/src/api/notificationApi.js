import { api } from "./client";
import { getAccessToken } from "../auth/authToken";
import { refreshAccessToken } from "../auth/authApi";

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
// JWT 인증을 사용하는 실시간 알림 SSE 구독
export function subscribeNotifications(userId, onNotification) {
    if (userId == null) return () => {};

    const baseUrl = import.meta.env.VITE_API_URL || "";
    const url = `${baseUrl}/api/v1/notifications/subscribe`;

    let stopped = false;
    let controller = null;
    let retryTimer = null;
    let connectedBefore = false;
    let triedAuthRefresh = false;

    const connect = async () => {
        if (stopped) return;

        const token = getAccessToken();

        if (!token) return;

        controller = new AbortController();

        try {
            const response = await fetch(url, {
                method: "GET",
                headers: {
                    Authorization: `Bearer ${token}`,
                    Accept: "text/event-stream",
                },
                credentials: "omit",
                cache: "no-store",
                signal: controller.signal,
            });

            if (response.status === 401) {
                controller?.abort();
                if (!triedAuthRefresh) {
                    triedAuthRefresh = true;
                    try {
                        if (await refreshAccessToken()) {
                            retryTimer = setTimeout(connect, 0);
                        }
                    } catch (error) {
                        console.warn("SSE 인증 갱신 실패", error);
                    }
                }
                return;
            }
            if (response.status === 403) return;
            triedAuthRefresh = false;

            if (!response.ok || !response.body) {
                throw new Error(`SSE 연결 실패: ${response.status}`);
            }

            // 재연결 시 놓친 알림 다시 조회
            if (connectedBefore) {
                onNotification();
            }
            connectedBefore = true;

            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = "";

            while (!stopped) {
                const { value, done } = await reader.read();

                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                buffer = buffer.replace(/\r\n/g, "\n");

                let endIndex;

                while ((endIndex = buffer.indexOf("\n\n")) !== -1) {
                    const eventText = buffer.slice(0, endIndex);
                    buffer = buffer.slice(endIndex + 2);

                    const eventLine = eventText
                        .split("\n")
                        .find((line) => line.startsWith("event:"));

                    const eventType = eventLine?.slice(6).trim();

                    if (eventType === "notification" && !stopped) {
                        onNotification();
                    }
                }
            }
        } catch (error) {
            if (!stopped && error.name !== "AbortError") {
                console.error("SSE 연결 오류:", error);
            }
        }

        // 네트워크 단절이나 서버 연결 종료 시 재연결
        if (!stopped) {
            retryTimer = setTimeout(connect, 3000);
        }
    };

    void connect();

    // 로그아웃 또는 컴포넌트 종료 시 연결 해제
    return () => {
        stopped = true;
        clearTimeout(retryTimer);
        controller?.abort();
    };
}