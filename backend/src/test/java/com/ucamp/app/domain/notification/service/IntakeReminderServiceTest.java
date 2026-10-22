package com.ucamp.app.domain.notification.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로컬 MySQL의 schema.sql 예시 데이터를 기준으로 테스트한다. (실행 전 schema.sql 실행 필요)
 * 예시 데이터에 복용 기록이 없는 2026-10-23을 기준일로 사용한다.
 * - 김민지(user 1): 일정 2(점심 12:30)
 * - 김서준(user 2): 일정 4, 5, 6(아침 08:30)
 * 각 테스트는 @Transactional로 끝나면 롤백되므로 DB 데이터는 바뀌지 않는다.
 */
@SpringBootTest
@Transactional
class IntakeReminderServiceTest {

    private static final LocalDate OCT_23 = LocalDate.of(2026, 10, 23);

    @Autowired
    private IntakeReminderService intakeReminderService;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("알림 시각이 지나면 복용 알림이 생성된다")
    void createsReminder() {
        intakeReminderService.sendReminders(LocalDateTime.of(OCT_23, LocalTime.of(12, 31)));

        List<Object[]> rows = findByDedupKey("INTAKE:1:2026-10-23:12:30");
        assertThat(rows).hasSize(1);
        Object[] row = rows.get(0);
        assertThat(row[0]).isEqualTo("INTAKE_REMINDER");
        assertThat(row[1]).isEqualTo("점심 영양제 1개를 드실 시간이에요");
        assertThat(((Number) row[2]).longValue()).isEqualTo(1L);
    }

    @Test
    @DisplayName("같은 사용자·같은 시각의 일정은 알림 1개로 묶인다")
    void groupsSchedulesAtSameTime() {
        intakeReminderService.sendReminders(LocalDateTime.of(OCT_23, LocalTime.of(8, 31)));

        List<Object[]> rows = findByDedupKey("INTAKE:2:2026-10-23:08:30");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[1]).isEqualTo("아침 영양제 3개를 드실 시간이에요");
    }

    @Test
    @DisplayName("같은 시각으로 여러 번 실행해도 알림은 1개만 생성된다")
    void doesNotCreateDuplicate() {
        LocalDateTime now = LocalDateTime.of(OCT_23, LocalTime.of(12, 31));

        intakeReminderService.sendReminders(now);
        intakeReminderService.sendReminders(now);
        intakeReminderService.sendReminders(now.plusMinutes(1));

        assertThat(findByDedupKey("INTAKE:1:2026-10-23:12:30")).hasSize(1);
    }

    @Test
    @DisplayName("오늘 이미 복용했으면 알림이 생성되지 않는다")
    void skipsWhenAlreadyTaken() {
        em.createNativeQuery("""
                INSERT INTO intake_records
                    (intake_record_id, schedule_id, intake_date, status, completed_at, is_shared)
                VALUES (9999, 2, '2026-10-23', 'TAKEN', '2026-10-23 12:20:00', false)
                """).executeUpdate();

        intakeReminderService.sendReminders(LocalDateTime.of(OCT_23, LocalTime.of(12, 31)));

        assertThat(findByDedupKey("INTAKE:1:2026-10-23:12:30")).isEmpty();
    }

    @Test
    @DisplayName("조회 시작 시각은 5분 전이며, 자정 직후에는 00:00부터 조회한다")
    void rangeStart() {
        assertThat(IntakeReminderService.rangeStart(LocalTime.of(12, 31))).isEqualTo(LocalTime.of(12, 26));
        assertThat(IntakeReminderService.rangeStart(LocalTime.of(0, 2))).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    @DisplayName("dedup_key는 INTAKE:{사용자}:{날짜}:{시각} 형식이다")
    void dedupKey() {
        assertThat(IntakeReminderService.dedupKey(1L, OCT_23, LocalTime.of(8, 0)))
                .isEqualTo("INTAKE:1:2026-10-23:08:00");
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
