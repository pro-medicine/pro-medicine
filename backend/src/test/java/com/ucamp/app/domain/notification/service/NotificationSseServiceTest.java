package com.ucamp.app.domain.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * SSE 연결 보관소 테스트. 스프링 없이 서비스 객체만 만들어 확인한다.
 * (실제 브라우저 연결 전에는 SseEmitter가 보낸 이벤트를 내부에 쌓아 두므로 send가 실패하지 않는다.)
 */
class NotificationSseServiceTest {

    private final NotificationSseService sseService = new NotificationSseService();

    @Test
    @DisplayName("구독하면 연결이 보관되고, 같은 사용자가 여러 번 구독하면(탭 여러 개) 연결도 여러 개다")
    void subscribeKeepsConnections() {
        SseEmitter first = sseService.subscribe(1L);
        SseEmitter second = sseService.subscribe(1L);

        assertThat(first).isNotSameAs(second);
        assertThat(first.getTimeout()).isEqualTo(NotificationSseService.TIMEOUT_MILLIS);
        assertThat(sseService.connectionCount(1L)).isEqualTo(2);
        assertThat(sseService.connectionCount(2L)).isZero();
    }

    @Test
    @DisplayName("연결을 지우면 연결 수가 줄고, 마지막 연결이면 0이 된다")
    void removeConnection() {
        SseEmitter first = sseService.subscribe(1L);
        SseEmitter second = sseService.subscribe(1L);

        sseService.remove(1L, first);
        assertThat(sseService.connectionCount(1L)).isEqualTo(1);

        sseService.remove(1L, second);
        assertThat(sseService.connectionCount(1L)).isZero();
    }

    @Test
    @DisplayName("연결이 없는 사용자에게 알림 신호를 보내도 오류가 나지 않는다")
    void sendToUserWithoutConnection() {
        assertThatCode(() -> sseService.sendNotification(99L)).doesNotThrowAnyException();
        assertThatCode(sseService::sendHeartbeat).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("알림 신호와 하트비트를 보내도 정상 연결은 유지된다")
    void sendKeepsHealthyConnection() {
        sseService.subscribe(1L);

        sseService.sendNotification(1L);
        sseService.sendHeartbeat();

        assertThat(sseService.connectionCount(1L)).isEqualTo(1);
    }

    @Test
    @DisplayName("전송에 실패한 연결(브라우저가 닫힘)은 지우고, 다른 연결은 유지한다")
    void removeBrokenConnectionOnSendFailure() {
        sseService.subscribe(1L);                      // 정상 연결
        sseService.register(1L, new BrokenEmitter());  // 이미 끊긴 연결

        sseService.sendNotification(1L);

        assertThat(sseService.connectionCount(1L)).isEqualTo(1);
    }

    /** 보낼 때마다 IOException이 나는 연결 (브라우저가 이미 닫힌 상황) */
    private static class BrokenEmitter extends SseEmitter {
        @Override
        public void send(SseEventBuilder builder) throws IOException {
            throw new IOException("connection closed");
        }
    }
}
