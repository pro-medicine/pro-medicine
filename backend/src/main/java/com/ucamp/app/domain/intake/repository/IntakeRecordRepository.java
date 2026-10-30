package com.ucamp.app.domain.intake.repository;


import com.ucamp.app.domain.intake.dto.TakenIntake;
import com.ucamp.app.domain.intake.entity.IntakeRecord;
import com.ucamp.app.domain.intake.entity.IntakeSchedule;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

// 마이페이지 복용 통계용 조회 (읽기 전용)
public interface IntakeRecordRepository extends Repository<IntakeRecord, Long> {

    @Query("""
            SELECT COUNT(up) FROM UserProduct up
            WHERE up.user.id = :userId
              AND up.isActive = true
              AND up.productKind = 'SUPPLEMENT'
            """)
    long countActiveSupplements(@Param("userId") Long userId);

//    // 복용 중인 제품의 켜져 있는 일정 (복용 시작일 계산을 위해 제품도 함께 조회) -> 이 부분 IntakeScheduleRepository로 이동
//    @Query("""
//            SELECT s FROM IntakeSchedule s
//            JOIN FETCH s.userProduct up
//            WHERE up.user.id = :userId
//              AND up.isActive = true
//              AND s.isEnabled = true
//            """)
//    List<IntakeSchedule> findActiveSchedules(@Param("userId") Long userId);

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


    // ===== 복용 체크 저장 (home에서 옮겨온 것) =====

    // 체크했는데 이미 기록이 있으면 TAKEN으로 바꿈
    @Modifying
    @Query(value = """
            UPDATE intake_records
            SET status = 'TAKEN', completed_at = NOW()
            WHERE schedule_id = :scheduleId AND intake_date = :date
            """, nativeQuery = true)
    int updateTaken(@Param("scheduleId") Long scheduleId, @Param("date") LocalDate date);

    // 기록이 없으면 새로 만듦 (AUTO_INCREMENT가 없어서 MAX + 1로 PK 지정)
    @Modifying
    @Query(value = """
            INSERT INTO intake_records
                (intake_record_id, schedule_id, intake_date, status, completed_at, is_shared)
            SELECT COALESCE(MAX(intake_record_id), 0) + 1, :scheduleId, :date, 'TAKEN', NOW(), FALSE
            FROM intake_records
            """, nativeQuery = true)
    int insertTaken(@Param("scheduleId") Long scheduleId, @Param("date") LocalDate date);

    // 체크 해제 = 기록 삭제
    @Modifying
    @Query(value = """
            DELETE FROM intake_records
            WHERE schedule_id = :scheduleId AND intake_date = :date
            """, nativeQuery = true)
    int deleteRecord(@Param("scheduleId") Long scheduleId, @Param("date") LocalDate date);

    @Query("""
        SELECT new com.ucamp.app.domain.intake.dto.TakenIntake(r.schedule.scheduleId, r.intakeDate)
        FROM IntakeRecord r
        WHERE r.schedule.userProduct.user.id IN :userIds
          AND r.status = 'TAKEN'
          AND r.intakeDate BETWEEN :from AND :to
        """)
    List<TakenIntake> findTakenIntakesByUserIds(@Param("userIds") Collection<Long> userIds,
                                                @Param("from") LocalDate from,
                                                @Param("to") LocalDate to);
}