package com.ucamp.app.domain.notification.event;

/**
 * 알림이 저장됐다는 이벤트. (NotificationService.create에서 발행)
 * 트랜잭션 커밋 후 NotificationSseListener가 받아서 받는 사람에게 실시간(SSE) 신호를 보낸다.
 *
 * @param receiverId 알림을 받는 사람
 */
public record NotificationCreatedEvent(Long receiverId) {
}
