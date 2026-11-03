package com.ucamp.app.domain.notification.service;

import com.ucamp.app.domain.notification.event.IntakeStateChangedEvent;
import com.ucamp.app.domain.notification.event.NotificationCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림이 저장되면 받는 사람에게 실시간(SSE) 신호를 보낸다.
 * - 커밋 후에 보낸다: 커밋 전에 보내면 프론트가 바로 다시 조회해도 새 알림이 아직 안 보일 수 있다.
 * - 저장이 롤백되면 보내지 않는다.
 * - fallbackExecution = true: 트랜잭션 밖에서 발행된 경우에도 바로 보낸다.
 * 전송 실패(브라우저 종료 등)는 NotificationSseService가 처리하므로 알림 저장에는 영향이 없다.
 */
@Component
@RequiredArgsConstructor
public class NotificationSseListener {

    private final NotificationSseService notificationSseService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        notificationSseService.sendNotification(event.receiverId());
    }

    /**
     * 복용 체크·해제가 저장되면 본인의 다른 화면(알림 패널·빨간 점·홈, 다른 기기 포함)이 다시 조회하도록 신호를 보낸다.
     * 체크하면 그 시각의 복용·놓침 알림이 사라지고, 해제하면 다시 나타난다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onIntakeStateChanged(IntakeStateChangedEvent event) {
        notificationSseService.sendRefresh(event.userId());
    }
}
