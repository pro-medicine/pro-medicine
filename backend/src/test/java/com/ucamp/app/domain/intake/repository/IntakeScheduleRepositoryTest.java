package com.ucamp.app.domain.intake.repository;

import com.ucamp.app.domain.intake.entity.IntakeSchedule;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로컬 MySQL의 schema.sql 예시 데이터를 기준으로 테스트한다. (실행 전 schema.sql 실행 필요)
 * - 김민지(user 1): 일정 1(08:00), 2(12:30), 3(19:00). 10/21에는 일정 1만 복용
 * - 김서준(user 2): 일정 4, 5, 6 모두 08:30. 10/21에 모두 복용
 * - 이유진(user 3): 일정 7(12:00), 복용 시작일 2026-10-01
 * 각 테스트는 @Transactional로 끝나면 롤백되므로 DB 데이터는 바뀌지 않는다.
 */
@SpringBootTest
@Transactional
class IntakeScheduleRepositoryTest {

    private static final LocalDate OCT_21 = LocalDate.of(2026, 10, 21);
    private static final LocalDate OCT_22 = LocalDate.of(2026, 10, 22);

    @Autowired
    private IntakeScheduleRepository intakeScheduleRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("알림 시각이 범위 안이고 오늘 복용 기록이 없으면 조회된다")
    void findsScheduleInRange() {
        List<IntakeSchedule> result = intakeScheduleRepository.findReminderTargets(
                OCT_21, LocalTime.of(12, 26), LocalTime.of(12, 31));

        assertThat(scheduleIds(result)).containsExactly(2L);
    }

    @Test
    @DisplayName("알림 시각이 범위 밖이면 조회되지 않는다")
    void excludesScheduleOutOfRange() {
        List<IntakeSchedule> result = intakeScheduleRepository.findReminderTargets(
                OCT_21, LocalTime.of(12, 31), LocalTime.of(12, 36));

        assertThat(scheduleIds(result)).doesNotContain(2L);
    }

    @Test
    @DisplayName("오늘 이미 복용 기록이 있으면 조회되지 않는다")
    void excludesScheduleAlreadyTaken() {
        // 10/21에는 일정 1(08:00) 복용 기록이 있음
        List<IntakeSchedule> taken = intakeScheduleRepository.findReminderTargets(
                OCT_21, LocalTime.of(7, 56), LocalTime.of(8, 1));
        // 10/22에는 복용 기록이 없음
        List<IntakeSchedule> notTaken = intakeScheduleRepository.findReminderTargets(
                OCT_22, LocalTime.of(7, 56), LocalTime.of(8, 1));

        assertThat(taken).isEmpty();
        assertThat(scheduleIds(notTaken)).containsExactly(1L);
    }

    @Test
    @DisplayName("같은 시각의 일정은 모두 조회된다")
    void findsAllSchedulesAtSameTime() {
        List<IntakeSchedule> result = intakeScheduleRepository.findReminderTargets(
                OCT_22, LocalTime.of(8, 26), LocalTime.of(8, 31));

        assertThat(scheduleIds(result)).containsExactlyInAnyOrder(4L, 5L, 6L);
    }

    @Test
    @DisplayName("일정 알림이 꺼져 있으면 조회되지 않는다")
    void excludesDisabledSchedule() {
        em.createNativeQuery("UPDATE intake_schedules SET is_enabled = false WHERE schedule_id = 2")
                .executeUpdate();

        List<IntakeSchedule> result = intakeScheduleRepository.findReminderTargets(
                OCT_21, LocalTime.of(12, 26), LocalTime.of(12, 31));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("영양제 복용을 중단했으면 조회되지 않는다")
    void excludesInactiveProduct() {
        em.createNativeQuery("UPDATE user_products SET is_active = false WHERE user_product_id = 2")
                .executeUpdate();

        List<IntakeSchedule> result = intakeScheduleRepository.findReminderTargets(
                OCT_21, LocalTime.of(12, 26), LocalTime.of(12, 31));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("복용 시작일 전이면 조회되지 않는다")
    void excludesBeforeStartDate() {
        // 일정 7(12:00)의 복용 시작일은 2026-10-01
        List<IntakeSchedule> beforeStart = intakeScheduleRepository.findReminderTargets(
                LocalDate.of(2026, 9, 30), LocalTime.of(11, 56), LocalTime.of(12, 1));
        List<IntakeSchedule> afterStart = intakeScheduleRepository.findReminderTargets(
                OCT_22, LocalTime.of(11, 56), LocalTime.of(12, 1));

        assertThat(beforeStart).isEmpty();
        assertThat(scheduleIds(afterStart)).containsExactly(7L);
    }

    @Test
    @DisplayName("조회 결과에 영양제와 사용자 정보가 함께 로딩된다 (JOIN FETCH)")
    void fetchesUserProductAndUser() {
        List<IntakeSchedule> result = intakeScheduleRepository.findReminderTargets(
                OCT_21, LocalTime.of(12, 26), LocalTime.of(12, 31));

        IntakeSchedule schedule = result.get(0);
        assertThat(Hibernate.isInitialized(schedule.getUserProduct())).isTrue();
        assertThat(Hibernate.isInitialized(schedule.getUserProduct().getUser())).isTrue();
        assertThat(schedule.getUserProduct().getUser().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("알림 시각이 범위의 시작 또는 끝과 같아도 조회된다")
    void includesRangeBoundaries() {
        // 일정 2의 알림 시각은 12:30
        List<IntakeSchedule> atStart = intakeScheduleRepository.findReminderTargets(
                OCT_21, LocalTime.of(12, 30), LocalTime.of(12, 35));
        List<IntakeSchedule> atEnd = intakeScheduleRepository.findReminderTargets(
                OCT_21, LocalTime.of(12, 25), LocalTime.of(12, 30));

        assertThat(scheduleIds(atStart)).contains(2L);
        assertThat(scheduleIds(atEnd)).contains(2L);
    }

    private List<Long> scheduleIds(List<IntakeSchedule> schedules) {
        return schedules.stream().map(IntakeSchedule::getScheduleId).toList();
    }
}
