package com.ucamp.app.domain.notification.service;

import com.ucamp.app.domain.notification.dto.NotificationResponse;
import com.ucamp.app.domain.notification.entity.NotificationCategory;
import com.ucamp.app.domain.notification.entity.NotificationType;
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
 * 팔로우 요청·수락 알림 테스트.
 * 테스트 데이터는 sql/follow-notification-fixture.sql
 * - 9401이 9402에게 팔로우를 요청하고, 9402가 수락하는 상황
 *
 * 이벤트 처리(onFollowRequested, onFollowAccepted)는 커밋 후에 실행되어 롤백되는 테스트에서는 동작하지 않으므로,
 * 실제 저장을 하는 notifyFollowRequested, notifyFollowAccepted를 직접 호출해 확인한다.
 */
@SpringBootTest
@Transactional
@Sql("/sql/follow-notification-fixture.sql")
class FollowNotificationServiceTest {

    private static final Long FOLLOWER = 9401L;    // 요청한 사람
    private static final Long FOLLOWING = 9402L;   // 요청을 받고 수락한 사람

    @Autowired
    private FollowNotificationService followNotificationService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("팔로우 요청 시 요청을 받은 사람에게 NEW_FOLLOWER 알림이 생성된다")
    void notifiesFollowRequest() {
        followNotificationService.notifyFollowRequested(FOLLOWER, FOLLOWING);

        List<Object[]> rows = findByDedupKey("FOLLOW:9402:9401");
        assertThat(rows).hasSize(1);
        Object[] row = rows.get(0);
        assertThat(row[0]).isEqualTo("NEW_FOLLOWER");
        assertThat(row[1]).isEqualTo("팔로우테스트9401님이 팔로우를 요청했어요");
        assertThat(((Number) row[2]).longValue()).isEqualTo(FOLLOWING);   // 받는 사람
        assertThat(((Number) row[3]).longValue()).isEqualTo(FOLLOWER);    // 요청한 사람
    }

    @Test
    @DisplayName("팔로우 요청 알림은 여러 번 호출해도 1번만 생성된다")
    void requestDoesNotCreateDuplicate() {
        followNotificationService.notifyFollowRequested(FOLLOWER, FOLLOWING);
        followNotificationService.notifyFollowRequested(FOLLOWER, FOLLOWING);

        assertThat(countByType("NEW_FOLLOWER")).isEqualTo(1);
    }

    @Test
    @DisplayName("팔로우 수락 시 요청한 사람에게 FOLLOW_ACCEPTED 알림이 생성된다")
    void notifiesFollowAccepted() {
        followNotificationService.notifyFollowAccepted(FOLLOWER, FOLLOWING);

        List<Object[]> rows = findByDedupKey("FOLLOW_ACCEPTED:9401:9402");
        assertThat(rows).hasSize(1);
        Object[] row = rows.get(0);
        assertThat(row[0]).isEqualTo("FOLLOW_ACCEPTED");
        assertThat(row[1]).isEqualTo("팔로우테스트9402님이 팔로우 요청을 수락했어요");
        assertThat(((Number) row[2]).longValue()).isEqualTo(FOLLOWER);    // 받는 사람
        assertThat(((Number) row[3]).longValue()).isEqualTo(FOLLOWING);   // 수락한 사람
    }

    @Test
    @DisplayName("팔로우 수락 알림은 여러 번 호출해도 1번만 생성된다")
    void acceptDoesNotCreateDuplicate() {
        followNotificationService.notifyFollowAccepted(FOLLOWER, FOLLOWING);
        followNotificationService.notifyFollowAccepted(FOLLOWER, FOLLOWING);

        assertThat(countByType("FOLLOW_ACCEPTED")).isEqualTo(1);
    }

    @Test
    @DisplayName("알림을 일으킨 사용자가 없으면 알림이 생성되지 않는다")
    void noNotificationWhenActorMissing() {
        followNotificationService.notifyFollowRequested(99999L, FOLLOWING);
        followNotificationService.notifyFollowAccepted(FOLLOWER, 99999L);

        assertThat(countByType("NEW_FOLLOWER")).isZero();
        assertThat(countByType("FOLLOW_ACCEPTED")).isZero();
    }

    @Test
    @DisplayName("수락 알림은 알림 목록의 친구 탭(FRIEND)에 나오고 복용 탭(INTAKE)에는 나오지 않는다")
    void acceptedNotificationIsInFriendCategory() {
        followNotificationService.notifyFollowAccepted(FOLLOWER, FOLLOWING);

        List<NotificationResponse> friend = notificationService.getNotifications(FOLLOWER, NotificationCategory.FRIEND);
        List<NotificationResponse> intake = notificationService.getNotifications(FOLLOWER, NotificationCategory.INTAKE);

        assertThat(friend).extracting(NotificationResponse::type).containsExactly(NotificationType.FOLLOW_ACCEPTED);
        assertThat(intake).isEmpty();
    }

    @Test
    @DisplayName("dedup_key는 FOLLOW:{받는 사람}:{요청한 사람}, FOLLOW_ACCEPTED:{받는 사람}:{수락한 사람} 형식이다")
    void dedupKey() {
        assertThat(FollowNotificationService.requestedDedupKey(2L, 1L)).isEqualTo("FOLLOW:2:1");
        assertThat(FollowNotificationService.acceptedDedupKey(1L, 2L)).isEqualTo("FOLLOW_ACCEPTED:1:2");
    }

    private long countByType(String type) {
        return ((Number) em.createNativeQuery("""
                        SELECT COUNT(*) FROM notifications
                        WHERE notification_type = :type
                          AND user_id IN (9401, 9402)
                        """)
                .setParameter("type", type)
                .getSingleResult()).longValue();
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
