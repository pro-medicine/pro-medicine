package com.ucamp.app.domain.notification.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 친구 복용 완료 알림 테스트.
 * 테스트 데이터는 sql/friend-goal-fixture.sql (2026-10-23 기준)
 * - 9301(공유 O): 일정 2개 중 1개 복용 / 팔로워 9302, 9303(수락), 9304(대기)
 * - 9305(공유 X): 일정 1개 모두 복용 / 팔로워 9306(수락)
 *
 * 이벤트 처리(onIntakeChecked)는 커밋 후에 실행되어 롤백되는 테스트에서는 동작하지 않으므로,
 * 실제 판단과 저장을 하는 notifyIfGoalAchieved를 직접 호출해 확인한다.
 */
@SpringBootTest
@Transactional
@Sql("/sql/friend-goal-fixture.sql")
class FriendGoalNotificationServiceTest {

    private static final LocalDate OCT_23 = LocalDate.of(2026, 10, 23);

    @Autowired
    private FriendGoalNotificationService friendGoalNotificationService;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("아직 다 먹지 않았으면 알림이 생성되지 않는다")
    void noNotificationWhenNotAllTaken() {
        friendGoalNotificationService.notifyIfGoalAchieved(9301L, OCT_23);

        assertThat(countFriendGoal(9301L)).isZero();
    }

    @Test
    @DisplayName("모두 먹으면 수락된 팔로워에게만 알림이 생성된다")
    void notifiesAcceptedFollowers() {
        takeDinner();

        friendGoalNotificationService.notifyIfGoalAchieved(9301L, OCT_23);

        assertThat(findByDedupKey("FRIEND_GOAL:9302:9301:2026-10-23")).hasSize(1);
        assertThat(findByDedupKey("FRIEND_GOAL:9303:9301:2026-10-23")).hasSize(1);
        assertThat(findByDedupKey("FRIEND_GOAL:9304:9301:2026-10-23")).isEmpty();   // 대기 중
        assertThat(countFriendGoal(9301L)).isEqualTo(2);
    }

    @Test
    @DisplayName("알림에는 완료한 친구의 닉네임 문구와 완료한 친구 id가 담긴다")
    void notificationContent() {
        takeDinner();

        friendGoalNotificationService.notifyIfGoalAchieved(9301L, OCT_23);

        Object[] row = findByDedupKey("FRIEND_GOAL:9302:9301:2026-10-23").get(0);
        assertThat(row[0]).isEqualTo("FRIEND_GOAL_ACHIEVED");
        assertThat(row[1]).isEqualTo("목표테스트9301님이 오늘 영양제 섭취를 완료했어요");
        assertThat(((Number) row[2]).longValue()).isEqualTo(9302L);   // 받는 사람
        assertThat(((Number) row[3]).longValue()).isEqualTo(9301L);   // 완료한 친구
    }

    @Test
    @DisplayName("여러 번 호출해도(체크 해제 후 재체크) 팔로워마다 하루 1번만 생성된다")
    void doesNotCreateDuplicate() {
        takeDinner();

        friendGoalNotificationService.notifyIfGoalAchieved(9301L, OCT_23);
        friendGoalNotificationService.notifyIfGoalAchieved(9301L, OCT_23);

        assertThat(countFriendGoal(9301L)).isEqualTo(2);
    }

    @Test
    @DisplayName("복용 현황을 공유하지 않는 사용자는 다 먹어도 알림이 생성되지 않는다")
    void noNotificationWhenNotSharing() {
        friendGoalNotificationService.notifyIfGoalAchieved(9305L, OCT_23);

        assertThat(countFriendGoal(9305L)).isZero();
    }

    @Test
    @DisplayName("오늘 먹어야 할 일정이 없는 사용자는 완료로 보지 않는다")
    void noScheduleIsNotAchieved() {
        assertThat(friendGoalNotificationService.isGoalAchieved(9302L, OCT_23)).isFalse();
    }

    @Test
    @DisplayName("dedup_key는 FRIEND_GOAL:{받는 사람}:{완료한 사람}:{날짜} 형식이다")
    void dedupKey() {
        assertThat(FriendGoalNotificationService.dedupKey(2L, 1L, OCT_23))
                .isEqualTo("FRIEND_GOAL:2:1:2026-10-23");
    }

    private void takeDinner() {
        em.createNativeQuery("""
                INSERT INTO intake_records
                    (intake_record_id, schedule_id, intake_date, status, completed_at, is_shared)
                VALUES (9302, 9302, '2026-10-23', 'TAKEN', '2026-10-23 19:05:00', false)
                """).executeUpdate();
    }

    private long countFriendGoal(Long achieverId) {
        return ((Number) em.createNativeQuery("""
                        SELECT COUNT(*) FROM notifications
                        WHERE notification_type = 'FRIEND_GOAL_ACHIEVED'
                          AND actor_user_id = :achieverId
                        """)
                .setParameter("achieverId", achieverId)
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
