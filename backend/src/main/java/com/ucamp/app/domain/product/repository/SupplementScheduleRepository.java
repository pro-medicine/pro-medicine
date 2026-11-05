package com.ucamp.app.domain.product.repository;

import com.ucamp.app.domain.intake.entity.IntakeSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// 마이페이지 "내가 먹는 영양제"에서 쓰는 복용 일정 조회·변경
// intake_schedules 테이블을 쓰지만, 알림 쪽 IntakeScheduleRepository를 건드리지 않으려고 따로 둠
public interface SupplementScheduleRepository extends JpaRepository<IntakeSchedule, Long> {

    // 여러 제품의 켜진 일정을 한 번에 조회 (빈 목록으로 호출하지 말 것)
    // IN :userProductIds → 제품 id 목록 [1, 2, 3]에 속한 일정만, 알림 시각 순서로
    @Query("""
            SELECT s FROM IntakeSchedule s
            WHERE s.userProduct.userProductId IN :userProductIds
              AND s.isEnabled = true
            ORDER BY s.remindAt, s.scheduleId
            """)
    List<IntakeSchedule> findEnabledByUserProductIds(@Param("userProductIds") List<Long> userProductIds);

    // 제품 하나의 켜진 일정을 모두 끈다 (복용 일정 수정할 때 기존 일정 정리)
    // 복용 기록이 일정을 가리키고 있어서 지우지 않고 is_enabled만 false로 바꾼다
    // @Modifying: 조회가 아니라 값을 바꾸는 쿼리라는 표시
    @Modifying
    @Query("""
            UPDATE IntakeSchedule s
            SET s.isEnabled = false
            WHERE s.userProduct.userProductId = :userProductId
              AND s.isEnabled = true
            """)
    void disableAllByUserProductId(@Param("userProductId") Long userProductId);
}
