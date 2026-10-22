package com.ucamp.app.domain.intake.repository;

import com.ucamp.app.domain.intake.dto.TakenIntake;
import com.ucamp.app.domain.intake.entity.IntakeRecord;
import com.ucamp.app.domain.intake.entity.IntakeSchedule;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

// 마이페이지 복용 통계용 조회 (읽기 전용)
public interface IntakeStatsRepository extends Repository<IntakeRecord, Long> {

    @Query("""
            SELECT COUNT(up) FROM UserProduct up
            WHERE up.user.id = :userId
              AND up.isActive = true
              AND up.productKind = 'SUPPLEMENT'
            """)
    long countActiveSupplements(@Param("userId") Long userId);

    // 복용 중인 제품의 켜져 있는 일정 (복용 시작일 계산을 위해 제품도 함께 조회)
    @Query("""
            SELECT s FROM IntakeSchedule s
            JOIN FETCH s.userProduct up
            WHERE up.user.id = :userId
              AND up.isActive = true
              AND s.isEnabled = true
            """)
    List<IntakeSchedule> findActiveSchedules(@Param("userId") Long userId);

    @Query("""
            SELECT new com.ucamp.app.domain.intake.dto.TakenIntake(r.schedule.scheduleId, r.intakeDate)
            FROM IntakeRecord r
            WHERE r.schedule.userProduct.user.id = :userId
              AND r.status = 'TAKEN'
              AND r.intakeDate BETWEEN :from AND :to
            """)
    List<TakenIntake> findTakenIntakes(@Param("userId") Long userId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);
}
