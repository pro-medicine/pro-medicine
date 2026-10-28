package com.ucamp.app.domain.notification.service;

import com.ucamp.app.domain.notification.entity.NotificationType;
import com.ucamp.app.domain.notification.event.NotificationCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 알림 저장 시 실시간 신호용 이벤트가 발행되는지 테스트.
 * 테스트 데이터: sql/follow-notification-fixture.sql (사용자 9401, 9402)
 *
 * 실제 SSE 전송은 커밋 후에 실행되어 롤백되는 테스트에서는 동작하지 않으므로,
 * 여기서는 이벤트 발행까지만 확인하고 전송은 NotificationSseListenerTest와 수동 확인으로 검증한다.
 */
@SpringBootTest
@Transactional
@RecordApplicationEvents   // 테스트 중 발행된 이벤트를 기록
@Sql("/sql/follow-notification-fixture.sql")
class NotificationCreatedEventTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ApplicationEvents events;

    @Test
    @DisplayName("알림을 저장하면 받는 사람 id가 담긴 NotificationCreatedEvent가 발행된다")
    void publishEventOnCreate() {
        notificationService.create(9402L, NotificationType.NEW_FOLLOWER, "팔로우테스트9401님이 팔로우를 요청했어요",
                9401L, null, null, "SSE-TEST:9402:9401");

        List<NotificationCreatedEvent> published = events.stream(NotificationCreatedEvent.class).toList();
        assertThat(published).hasSize(1);
        assertThat(published.get(0).receiverId()).isEqualTo(9402L);
    }

    @Test
    @DisplayName("중복이라 저장되지 않아도 이벤트는 발행된다 (프론트는 다시 조회만 하므로 문제없음)")
    void publishEventEvenWhenDuplicate() {
        notificationService.create(9402L, NotificationType.NEW_FOLLOWER, "제목",
                9401L, null, null, "SSE-TEST:DUP");
        notificationService.create(9402L, NotificationType.NEW_FOLLOWER, "제목",
                9401L, null, null, "SSE-TEST:DUP");

        assertThat(events.stream(NotificationCreatedEvent.class).count()).isEqualTo(2);
    }
}
