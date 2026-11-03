package com.ucamp.app.domain.notification.service;

import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 친구 챙겨주기 테스트. 테스트 데이터는 sql/friend-goal-fixture.sql (2026-10-23 기준)
 * - 9301(공유 O): 일정 2개 중 1개 복용 / 팔로워 9302, 9303(수락), 9304(대기)
 * - 9305(공유 X): 일정 1개 모두 복용 / 팔로워 9306(수락)
 */
@SpringBootTest
@Transactional
@Sql("/sql/friend-goal-fixture.sql")
class FriendNudgeServiceTest {

    private static final LocalDate OCT_23 = LocalDate.of(2026, 10, 23);

    @Autowired
    private FriendNudgeService friendNudgeService;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("팔로우 중인 친구에게 챙겨주기 알림을 보낸다")
    void sendsNudge() {
        friendNudgeService.nudge(9302L, 9301L, OCT_23);

        List<Object[]> rows = findByDedupKey("NUDGE:9301:9302:2026-10-23");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[0]).isEqualTo("FRIEND_NUDGE");
        assertThat(rows.get(0)[1]).isEqualTo("목표테스트9302님이 영양제 챙겨 드시래요");
        assertThat(((Number) rows.get(0)[2]).longValue()).isEqualTo(9301L);
    }

    @Test
    @DisplayName("같은 친구에게 하루 두 번은 보낼 수 없다")
    void onlyOncePerDay() {
        friendNudgeService.nudge(9302L, 9301L, OCT_23);

        assertThatThrownBy(() -> friendNudgeService.nudge(9302L, 9301L, OCT_23))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NUDGE_ALREADY_SENT);
        // 다음 날은 다시 보낼 수 있다
        friendNudgeService.nudge(9302L, 9301L, OCT_23.plusDays(1));
        assertThat(findByDedupKey("NUDGE:9301:9302:2026-10-24")).hasSize(1);
    }

    @Test
    @DisplayName("팔로우가 수락되지 않았으면 보낼 수 없다")
    void requiresAcceptedFollow() {
        assertThatThrownBy(() -> friendNudgeService.nudge(9304L, 9301L, OCT_23))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NUDGE_NOT_FOLLOWING);
    }

    @Test
    @DisplayName("복용 현황을 공유하는 친구가 오늘 다 먹었으면 보낼 수 없다")
    void rejectsWhenFriendAlreadyCompleted() {
        em.createNativeQuery("""
                INSERT INTO intake_records (intake_record_id, schedule_id, intake_date, status, completed_at, is_shared)
                VALUES (99302, 9302, '2026-10-23', 'TAKEN', '2026-10-23 19:05:00', FALSE)
                """).executeUpdate();

        assertThatThrownBy(() -> friendNudgeService.nudge(9303L, 9301L, OCT_23))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NUDGE_ALREADY_COMPLETED);
    }

    @Test
    @DisplayName("복용 현황을 공유하지 않는 친구는 완료 여부를 확인하지 않고 보낸다")
    void sendsToPrivateFriendWithoutCheckingCompletion() {
        friendNudgeService.nudge(9306L, 9305L, OCT_23);

        assertThat(findByDedupKey("NUDGE:9305:9306:2026-10-23")).hasSize(1);
    }

    // 결과: [notification_type, title, user_id]
    @SuppressWarnings("unchecked")
    private List<Object[]> findByDedupKey(String dedupKey) {
        return em.createNativeQuery("""
                        SELECT notification_type, title, user_id
                        FROM notifications
                        WHERE dedup_key = :dedupKey
                        """)
                .setParameter("dedupKey", dedupKey)
                .getResultList();
    }
}
