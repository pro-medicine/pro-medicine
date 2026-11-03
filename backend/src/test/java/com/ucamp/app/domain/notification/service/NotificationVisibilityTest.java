package com.ucamp.app.domain.notification.service;

import com.ucamp.app.domain.notification.dto.NotificationResponse;
import com.ucamp.app.domain.notification.entity.NotificationCategory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지난 복용 알림이 목록·안 읽은 개수에서 빠지는지 테스트.
 * 사용자는 sql/intake-reminder-fixture.sql의 9001을 쓰고, 알림은 테스트마다 직접 넣는다.
 * 12:30 일정 기준
 * - 복용 알림: 12:30 ~ 13:30 보임
 * - 복용 놓침 알림: 13:30에 생기고 14:30까지 보임
 * - 12:30 일정을 먹음·건너뜀 체크하면 시간과 상관없이 둘 다 숨김
 */
@SpringBootTest
@Transactional
@Sql("/sql/intake-reminder-fixture.sql")
class NotificationVisibilityTest {

    private static final LocalDateTime INTAKE_TIME = LocalDateTime.of(2026, 10, 23, 12, 30);

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EntityManager em;

    @BeforeEach
    void insertNotifications() {
        insert(99101, "INTAKE_REMINDER", INTAKE_TIME, "V:REMINDER");
        insert(99102, "INTAKE_MISSED", INTAKE_TIME, "V:MISSED");
        insert(99103, "NEW_FOLLOWER", null, "V:FOLLOW");   // 다른 종류는 시간이 지나도 보임
    }

    @Test
    @DisplayName("복용 시각부터 1시간 안에는 복용 알림이 보인다")
    void reminderVisibleWithinOneHour() {
        assertThat(types(INTAKE_TIME.plusMinutes(59)))
                .containsExactlyInAnyOrder("INTAKE_REMINDER", "INTAKE_MISSED", "NEW_FOLLOWER");
    }

    @Test
    @DisplayName("1시간이 지나면 복용 알림은 사라지고 복용 놓침 알림은 보인다")
    void reminderHiddenAfterOneHour() {
        LocalDateTime now = INTAKE_TIME.plusMinutes(61);
        assertThat(types(now)).containsExactlyInAnyOrder("INTAKE_MISSED", "NEW_FOLLOWER");
        assertThat(notificationService.getUnreadCount(9001L, now).unreadCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("2시간이 지나면 복용 놓침 알림도 사라진다")
    void missedHiddenAfterTwoHours() {
        LocalDateTime now = INTAKE_TIME.plusMinutes(121);
        assertThat(types(now)).containsExactly("NEW_FOLLOWER");
        assertThat(notificationService.getUnreadCount(9001L, now).unreadCount()).isEqualTo(1);
        assertThat(notificationService.getNotifications(9001L, NotificationCategory.INTAKE, now)).isEmpty();
    }

    @Test
    @DisplayName("그 시각의 일정을 복용 체크하면 복용 알림과 놓침 알림이 사라진다")
    void hiddenAfterIntakeChecked() {
        LocalDateTime now = INTAKE_TIME.plusMinutes(10);
        assertThat(notificationService.getUnreadCount(9001L, now).unreadCount()).isEqualTo(3);

        // 사용자 9001의 12:30 일정(9001)을 10/23에 복용
        em.createNativeQuery("""
                INSERT INTO intake_records (intake_record_id, schedule_id, intake_date, status, completed_at, is_shared)
                VALUES (99104, 9001, '2026-10-23', 'TAKEN', '2026-10-23 12:40:00', false)
                """).executeUpdate();

        assertThat(types(now)).containsExactly("NEW_FOLLOWER");
        assertThat(notificationService.getUnreadCount(9001L, now).unreadCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 날짜에 복용 체크한 것은 영향을 주지 않는다")
    void otherDateCheckDoesNotHide() {
        em.createNativeQuery("""
                INSERT INTO intake_records (intake_record_id, schedule_id, intake_date, status, completed_at, is_shared)
                VALUES (99105, 9001, '2026-10-22', 'TAKEN', '2026-10-22 12:40:00', false)
                """).executeUpdate();

        assertThat(types(INTAKE_TIME.plusMinutes(10)))
                .containsExactlyInAnyOrder("INTAKE_REMINDER", "INTAKE_MISSED", "NEW_FOLLOWER");
    }

    private List<String> types(LocalDateTime now) {
        return notificationService.getNotifications(9001L, null, now).stream()
                .map(NotificationResponse::type)
                .map(Enum::name)
                .toList();
    }

    private void insert(long id, String type, LocalDateTime scheduledAt, String dedupKey) {
        em.createNativeQuery("""
                        INSERT INTO notifications
                            (notification_id, user_id, notification_type, title, scheduled_at, sent_at, created_at, dedup_key)
                        VALUES (:id, 9001, :type, '테스트 알림', :scheduledAt, NOW(), NOW(), :dedupKey)
                        """)
                .setParameter("id", id)
                .setParameter("type", type)
                .setParameter("scheduledAt", scheduledAt)
                .setParameter("dedupKey", dedupKey)
                .executeUpdate();
    }
}
