package com.ucamp.app.domain.notification.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 안부지킴이 신청·수락 알림 테스트. 사용자는 sql/friend-goal-fixture.sql의 9301, 9302를 쓴다.
 * 이벤트 처리는 커밋 후에 실행되어 롤백되는 테스트에서는 동작하지 않으므로, 알림을 만드는 메서드를 직접 호출한다.
 */
@SpringBootTest
@Transactional
@Sql("/sql/friend-goal-fixture.sql")
class GuardianNotificationServiceTest {

    @Autowired
    private GuardianNotificationService guardianNotificationService;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("안부지킴이 신청을 받으면 신청 알림이 생긴다 (보낸 사람 = 신청한 사람)")
    void requested() {
        // 9302가 9301의 지킴이가 되겠다고 신청 (설정 id 15)
        guardianNotificationService.notifyRequested(15L, 9302L, 9301L);

        List<Object[]> rows = findByDedupKey("GUARDIAN_REQUEST:9301:15");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[0]).isEqualTo("GUARDIAN_REQUESTED");
        assertThat(rows.get(0)[1]).isEqualTo("목표테스트9302님이 안부지킴이를 신청했어요");
        assertThat(((Number) rows.get(0)[2]).longValue()).isEqualTo(9301L);
        assertThat(((Number) rows.get(0)[3]).longValue()).isEqualTo(9302L);
    }

    @Test
    @DisplayName("안부지킴이 신청이 수락되면 신청한 사람에게 수락 알림이 생긴다")
    void accepted() {
        guardianNotificationService.notifyAccepted(15L, 9302L, 9301L, 3);

        List<Object[]> rows = findByDedupKey("GUARDIAN_ACCEPTED:9302:15");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[0]).isEqualTo("GUARDIAN_ACCEPTED");
        assertThat(rows.get(0)[1])
                .isEqualTo("목표테스트9301님이 안부지킴이 신청을 수락했어요. 3일 이상 접속하지 않으면 알려드릴게요");
        assertThat(((Number) rows.get(0)[2]).longValue()).isEqualTo(9302L);
    }

    // 결과: [notification_type, title, user_id, actor_user_id]
    @SuppressWarnings("unchecked")
    private List<Object[]> findByDedupKey(String dedupKey) {
        return em.createNativeQuery("""
                        SELECT notification_type, title, user_id, actor_user_id
                        FROM notifications
                        WHERE dedup_key = :dedupKey
                        """)
                .setParameter("dedupKey", dedupKey)
                .getResultList();
    }
}
