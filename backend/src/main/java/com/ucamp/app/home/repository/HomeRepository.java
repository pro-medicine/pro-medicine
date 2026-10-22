package com.ucamp.app.home.repository;

import com.ucamp.app.home.entity.HomeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface HomeRepository extends JpaRepository<HomeEntity, Long> {

    // native query 결과를 받는 그릇 (SQL의 AS 별칭 = getter 이름)
    interface SupplementRow {
        Long getId();
        String getTiming();
        String getName();
        String getDesc();
        Number getTaken();   // MySQL은 true/false를 1/0으로 줌
    }

    @Query(value = "SELECT nickname FROM users WHERE user_id = :userId", nativeQuery = true)
    String findNickname(@Param("userId") Long userId);

    // 안 읽은 알림 개수
    @Query(value = """
            SELECT COUNT(*) FROM notifications
            WHERE user_id = :userId AND read_at IS NULL
            """, nativeQuery = true)
    long countUnread(@Param("userId") Long userId);

    // 그날 먹어야 할 일정 + 그날 복용 기록
    @Query(value = """
            SELECT s.schedule_id AS id,
                   CONCAT(
                       CASE s.meal_slot WHEN 'BREAKFAST' THEN '아침' WHEN 'LUNCH' THEN '점심'
                                        WHEN 'DINNER' THEN '저녁' ELSE s.meal_slot END,
                       ' ',
                       CASE s.meal_relation WHEN 'BEFORE' THEN '식전' WHEN 'AFTER' THEN '식후'
                                            ELSE s.meal_relation END
                   ) AS timing,
                   COALESCE(cp.product_name, up.manual_product_name) AS name,
                   COALESCE(cp.main_function, '') AS `desc`,
                   IFNULL(ir.status = 'TAKEN', 0) AS taken
            FROM intake_schedules s
            JOIN user_products up ON up.user_product_id = s.user_product_id
            LEFT JOIN catalog_products cp ON cp.catalog_product_id = up.catalog_product_id
            LEFT JOIN intake_records ir ON ir.schedule_id = s.schedule_id
                                       AND ir.intake_date = :date
            WHERE up.user_id = :userId
              AND up.is_active = TRUE
              AND s.is_enabled = TRUE
              AND (up.start_date IS NULL OR up.start_date <= :date)
            ORDER BY s.remind_at
            """, nativeQuery = true)
    List<SupplementRow> findTodaySupplements(@Param("userId") Long userId, @Param("date") LocalDate date);

    // 남의 일정은 체크 못 하게
    @Query(value = """
            SELECT COUNT(*)
            FROM intake_schedules s
            JOIN user_products up ON up.user_product_id = s.user_product_id
            WHERE s.schedule_id = :scheduleId AND up.user_id = :userId
            """, nativeQuery = true)
    long countMySchedule(@Param("userId") Long userId, @Param("scheduleId") Long scheduleId);

    @Modifying
    @Query(value = """
            UPDATE intake_records
            SET status = 'TAKEN', completed_at = NOW()
            WHERE schedule_id = :scheduleId AND intake_date = :date
            """, nativeQuery = true)
    int updateTaken(@Param("scheduleId") Long scheduleId, @Param("date") LocalDate date);

    // AUTO_INCREMENT가 없어서 MAX + 1로 PK 지정
    @Modifying
    @Query(value = """
            INSERT INTO intake_records
                (intake_record_id, schedule_id, intake_date, status, completed_at, is_shared)
            SELECT COALESCE(MAX(intake_record_id), 0) + 1, :scheduleId, :date, 'TAKEN', NOW(), FALSE
            FROM intake_records
            """, nativeQuery = true)
    int insertTaken(@Param("scheduleId") Long scheduleId, @Param("date") LocalDate date);

    @Modifying
    @Query(value = """
            DELETE FROM intake_records
            WHERE schedule_id = :scheduleId AND intake_date = :date
            """, nativeQuery = true)
    int deleteRecord(@Param("scheduleId") Long scheduleId, @Param("date") LocalDate date);
}