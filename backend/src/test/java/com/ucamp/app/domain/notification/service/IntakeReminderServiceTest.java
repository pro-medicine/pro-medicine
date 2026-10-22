package com.ucamp.app.domain.notification.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 테스트 데이터는 sql/intake-reminder-fixture.sql에서 넣는다. (schema.sql 예시 데이터에 의존하지 않음)
 * - 사용자 9001: 점심 12:30 일정 1개, 저녁 23:59 일정 1개
 * - 사용자 9002: 아침 08:30 일정 3개
 * 테이블 구조는 로컬 DB에 schema.sql로 만들어져 있어야 한다.
 * 각 테스트는 @Transactional로 끝나면 롤백되므로 DB 데이터는 바뀌지 않는다.
 */
@SpringBootTest
@Transactional
@Sql("/sql/intake-reminder-fixture.sql")
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

        List<Object[]> rows = findByDedupKey("INTAKE:9001:2026-10-23:12:30");
        assertThat(rows).hasSize(1);
        Object[] row = rows.get(0);
        assertThat(row[0]).isEqualTo("INTAKE_REMINDER");
        assertThat(row[1]).isEqualTo("점심 영양제 1개를 드실 시간이에요");
        assertThat(((Number) row[2]).longValue()).isEqualTo(9001L);
    }

    @Test
    @DisplayName("같은 사용자·같은 시각의 일정은 알림 1개로 묶인다")
    void groupsSchedulesAtSameTime() {
        intakeReminderService.sendReminders(LocalDateTime.of(OCT_23, LocalTime.of(8, 31)));

        List<Object[]> rows = findByDedupKey("INTAKE:9002:2026-10-23:08:30");
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

        assertThat(findByDedupKey("INTAKE:9001:2026-10-23:12:30")).hasSize(1);
    }

    @Test
    @DisplayName("오늘 이미 복용했으면 알림이 생성되지 않는다")
    void skipsWhenAlreadyTaken() {
        em.createNativeQuery("""
                INSERT INTO intake_records
                    (intake_record_id, schedule_id, intake_date, status, completed_at, is_shared)
                VALUES (99001, 9001, '2026-10-23', 'TAKEN', '2026-10-23 12:20:00', false)
                """).executeUpdate();

        intakeReminderService.sendReminders(LocalDateTime.of(OCT_23, LocalTime.of(12, 31)));

        assertThat(findByDedupKey("INTAKE:9001:2026-10-23:12:30")).isEmpty();
    }

    @Test
    @DisplayName("조회 범위가 자정을 넘으면 전날 일정도 전날 날짜로 알림이 생성된다")
    void createsPreviousDayReminderAfterMidnight() {
        // 10/24 00:02 실행 → 조회 범위는 10/23 23:57 ~ 10/24 00:02
        intakeReminderService.sendReminders(LocalDateTime.of(2026, 10, 24, 0, 2));

        List<Object[]> rows = findByDedupKey("INTAKE:9001:2026-10-23:23:59");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[1]).isEqualTo("저녁 영양제 1개를 드실 시간이에요");
        assertThat(findByDedupKey("INTAKE:9001:2026-10-24:23:59")).isEmpty();
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
