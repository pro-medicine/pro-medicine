package com.ucamp.app.domain.notification.service;

import com.ucamp.app.domain.notification.event.NotificationCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 알림 저장 이벤트를 받으면 받는 사람에게 SSE 신호를 보내는지 테스트. (스프링 없이 Mockito만 사용)
 */
class NotificationSseListenerTest {

    @Test
    @DisplayName("NotificationCreatedEvent를 받으면 받는 사람에게 알림 신호를 보낸다")
    void sendNotificationToReceiver() {
        NotificationSseService sseService = mock(NotificationSseService.class);
        NotificationSseListener listener = new NotificationSseListener(sseService);

        listener.onNotificationCreated(new NotificationCreatedEvent(9402L));

        verify(sseService).sendNotification(9402L);
    }
}
