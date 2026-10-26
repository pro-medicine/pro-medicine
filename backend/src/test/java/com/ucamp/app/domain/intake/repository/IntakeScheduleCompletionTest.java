package com.ucamp.app.domain.intake.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 오늘 복용 완료 판정 쿼리 테스트.
 * 테스트 데이터는 sql/intake-completion-fixture.sql (사용자 9101, 2026-10-23 기준)
 * - 오늘 먹어야 하는 일정: 9101(영양제), 9102(약) → 2개
 * - 제외: 9103(알림 꺼짐), 9104(복용 중단), 9105(시작일 전)
 * - 10/23에 9101만 TAKEN
 */
@SpringBootTest
@Transactional
@Sql("/sql/intake-completion-fixture.sql")
class IntakeScheduleCompletionTest {

    private static final Long USER_ID = 9101L;
    private static final LocalDate OCT_23 = LocalDate.of(2026, 10, 23);

    @Autowired
    private IntakeScheduleRepository intakeScheduleRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("오늘 먹어야 하는 일정은 복용 중·알림 켜짐·시작일 이후인 일정만 센다 (약 포함)")
    void countsTodaySchedules() {
        assertThat(intakeScheduleRepository.countTodaySchedules(USER_ID, OCT_23)).isEqualTo(2);
    }

    @Test
    @DisplayName("오늘 복용 완료한 일정 수를 센다")
    void countsTakenSchedules() {
        assertThat(intakeScheduleRepository.countTodayTakenSchedules(USER_ID, OCT_23)).isEqualTo(1);
    }

    @Test
    @DisplayName("남은 일정까지 복용하면 두 수가 같아진다 (모두 복용)")
    void allTakenWhenCountsMatch() {
        insertRecord(9102L, 9102L, "2026-10-23", "TAKEN");

        long total = intakeScheduleRepository.countTodaySchedules(USER_ID, OCT_23);
        long taken = intakeScheduleRepository.countTodayTakenSchedules(USER_ID, OCT_23);

        assertThat(taken).isEqualTo(total).isEqualTo(2);
    }

    @Test
    @DisplayName("건너뜀(SKIPPED)은 복용 완료로 세지 않는다")
    void skippedIsNotTaken() {
        insertRecord(9102L, 9102L, "2026-10-23", "SKIPPED");

        assertThat(intakeScheduleRepository.countTodayTakenSchedules(USER_ID, OCT_23)).isEqualTo(1);
    }

    @Test
    @DisplayName("제외된 일정(알림 꺼짐)을 복용해도 완료 수에 들어가지 않는다")
    void excludedScheduleIsNotCounted() {
        insertRecord(9103L, 9103L, "2026-10-23", "TAKEN");

        assertThat(intakeScheduleRepository.countTodayTakenSchedules(USER_ID, OCT_23)).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 날짜의 복용 기록은 세지 않는다")
    void countsOnlyGivenDate() {
        assertThat(intakeScheduleRepository.countTodayTakenSchedules(USER_ID, OCT_23.plusDays(1))).isZero();
    }

    @Test
    @DisplayName("일정이 없는 사용자는 0개다")
    void userWithoutSchedules() {
        assertThat(intakeScheduleRepository.countTodaySchedules(9102L, OCT_23)).isZero();
        assertThat(intakeScheduleRepository.countTodayTakenSchedules(9102L, OCT_23)).isZero();
    }

    private void insertRecord(Long recordId, Long scheduleId, String date, String status) {
        em.createNativeQuery("""
                        INSERT INTO intake_records
                            (intake_record_id, schedule_id, intake_date, status, completed_at, is_shared)
                        VALUES (:recordId, :scheduleId, :date, :status, NOW(), false)
                        """)
                .setParameter("recordId", recordId)
                .setParameter("scheduleId", scheduleId)
                .setParameter("date", date)
                .setParameter("status", status)
                .executeUpdate();
    }
}
