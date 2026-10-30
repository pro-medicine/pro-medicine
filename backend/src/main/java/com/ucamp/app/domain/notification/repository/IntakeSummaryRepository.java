package com.ucamp.app.domain.notification.repository;

import com.ucamp.app.domain.intake.entity.IntakeRecord;
import com.ucamp.app.domain.intake.entity.IntakeSchedule;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 홈 복용 알림 카드용 읽기 전용 조회.
 * intake 테이블은 복용 기능(intake 도메인) 담당이므로, 그쪽 Repository를 수정하지 않고 알림 쪽에 따로 둔다.
 * (save·delete가 없는 Repository를 상속해 읽기만 한다)
 */
public interface IntakeSummaryRepository extends Repository<IntakeSchedule, Long> {

    /**
     * 오늘 아직 울리지 않은 복용 알림 일정. (다음 알림 계산용)
     * - 복용 중인 제품 (is_active), 켜진 일정 (is_enabled), 복용 시작일이 지남
     * - 알림 시각이 지금보다 뒤
     * - 오늘 이미 복용(TAKEN)했거나 건너뛴(SKIPPED) 일정은 제외 (복용 알림 스케줄러와 같은 기준)
     * 제품 정보까지 함께 조회(JOIN FETCH)해서 이름을 꺼낼 때 추가 쿼리가 나가지 않게 한다.
     */
    @Query("""
            SELECT s FROM IntakeSchedule s
            JOIN FETCH s.userProduct up
            LEFT JOIN FETCH up.catalogProduct
            WHERE up.user.id = :userId
              AND up.isActive = true
              AND s.isEnabled = true
              AND (up.startDate IS NULL OR up.startDate <= :today)
              AND s.remindAt > :now
              AND NOT EXISTS (
                    SELECT r FROM IntakeRecord r
                    WHERE r.schedule = s
                      AND r.intakeDate = :today
                      AND r.status IN ('TAKEN', 'SKIPPED')
                  )
            ORDER BY s.remindAt, s.scheduleId
            """)
    List<IntakeSchedule> findUpcomingToday(@Param("userId") Long userId,
                                           @Param("today") LocalDate today,
                                           @Param("now") LocalTime now);

    /**
     * 기간 안에 복용 체크(TAKEN)한 기록과 그 일정. (제시간 복용률 계산용)
     * 체크한 시각(completed_at)이 있는 기록만 본다.
     * 지금은 꺼진 일정의 기록도 그때 먹은 기록이므로 포함한다.
     */
    @Query("""
            SELECT r FROM IntakeRecord r
            JOIN FETCH r.schedule s
            WHERE s.userProduct.user.id = :userId
              AND r.status = 'TAKEN'
              AND r.completedAt IS NOT NULL
              AND r.intakeDate BETWEEN :from AND :to
            """)
    List<IntakeRecord> findTakenRecords(@Param("userId") Long userId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);
}
