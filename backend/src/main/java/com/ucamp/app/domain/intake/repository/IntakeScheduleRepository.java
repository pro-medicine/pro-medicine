package com.ucamp.app.domain.intake.repository;

import com.ucamp.app.domain.intake.entity.IntakeSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface IntakeScheduleRepository extends JpaRepository<IntakeSchedule, Long> {

    /**
     * 복용 알림을 보내야 하는 일정을 조회한다.
     *
     * 조건
     * - 일정 알림이 켜져 있음 (is_enabled = true)
     * - 영양제를 복용 중임 (user_products.is_active = true)
     * - 복용 시작일이 없거나 오늘 이전임
     * - 알림 시각(remind_at)이 from 이상 to 이하
     * - 오늘 복용 기록(TAKEN, SKIPPED)이 없음
     *
     * 사용자 정보까지 함께 조회(JOIN FETCH)해서, 알림을 만들 때 추가 쿼리가 나가지 않게 한다.
     *
     * @param today 기준 날짜 (KST)
     * @param from  조회 시작 시각 (포함)
     * @param to    조회 끝 시각 (포함)
     */
    @Query("""
            SELECT s FROM IntakeSchedule s
            JOIN FETCH s.userProduct up
            JOIN FETCH up.user u
            WHERE s.isEnabled = true
              AND up.isActive = true
              AND (up.startDate IS NULL OR up.startDate <= :today)
              AND s.remindAt >= :from
              AND s.remindAt <= :to
              AND NOT EXISTS (
                  SELECT r FROM IntakeRecord r
                  WHERE r.schedule = s
                    AND r.intakeDate = :today
              )
            ORDER BY u.id, s.remindAt
            """)
    List<IntakeSchedule> findReminderTargets(@Param("today") LocalDate today,
                                             @Param("from") LocalTime from,
                                             @Param("to") LocalTime to);
}
