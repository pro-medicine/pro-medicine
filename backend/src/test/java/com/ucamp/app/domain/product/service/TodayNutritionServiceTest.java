package com.ucamp.app.domain.product.service;

import com.ucamp.app.domain.product.dto.TodayNutritionResponse;
import com.ucamp.app.domain.product.dto.TodayNutritionResponse.NutrientInfo;
import com.ucamp.app.domain.product.dto.TodayNutritionResponse.ScheduleNutrients;
import com.ucamp.app.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 홈 "오늘 먹은 영양소" 테스트. 데이터는 sql/today-nutrition-fixture.sql (사용자 9201, 2026-11-03)
 * - 일정 9201·9202: 같은 멀티비타민(하루 2번) → 한 번에 하루 함량의 절반
 * - 일정 9203: 오메가3 (EPA+DHA 합계) / 9204: 직접 입력(함량 모름) / 9205: 처방약(계산 대상 아님)
 */
@SpringBootTest
@Transactional
@Sql("/sql/today-nutrition-fixture.sql")
class TodayNutritionServiceTest {

    private static final LocalDate NOV_3 = LocalDate.of(2026, 11, 3);

    @Autowired
    private TodayNutritionService service;

    @Test
    @DisplayName("일정별 성분 함량: 하루 여러 번 먹는 영양제는 하루 함량을 나누고, 함량 모름·처방약은 빠진다")
    void scheduleAmounts() {
        TodayNutritionResponse r = service.getTodayNutrition(9201L, NOV_3);

        assertThat(r.schedules()).extracting(ScheduleNutrients::scheduleId).containsExactly(9201L, 9203L, 9202L);
        assertThat(amountsOf(r, 9201L)).containsExactlyInAnyOrderEntriesOf(
                Map.of("VITAMIN_C", 50.0, "VITAMIN_D", 5.0, "ZINC", 4.25));
        assertThat(amountsOf(r, 9203L)).containsExactlyInAnyOrderEntriesOf(Map.of("OMEGA3", 500.0));
        assertThat(r.unsupportedCount()).isEqualTo(1);   // 직접 입력 9204만 (처방약 9205는 세지 않음)
        assertThat(r.sample()).isFalse();
    }

    @Test
    @DisplayName("대표 성분은 먹지 않아도 보여주고, 권장량은 건강 정보가 없으면 남성 기준")
    void nutrientsIncludeBase() {
        TodayNutritionResponse r = service.getTodayNutrition(9201L, NOV_3);

        assertThat(r.nutrients()).extracting(NutrientInfo::code).containsExactly(
                "VITAMIN_B12", "VITAMIN_C", "VITAMIN_D", "CALCIUM", "MAGNESIUM", "ZINC", "IRON", "OMEGA3");
        assertThat(r.nutrients()).filteredOn(n -> n.code().equals("MAGNESIUM"))
                .singleElement().satisfies(n -> {
                    assertThat(n.name()).isEqualTo("마그네슘");
                    assertThat(n.unit()).isEqualTo("mg");
                    assertThat(n.recommended()).isEqualTo(370);
                });
    }

    @Test
    @DisplayName("오늘 일정이 없어도 대표 성분은 0%로 보여줄 수 있게 내려준다")
    void noSchedules() {
        TodayNutritionResponse r = service.getTodayNutrition(9201L, LocalDate.of(2025, 12, 1)); // 복용 시작 전

        assertThat(r.schedules()).isEmpty();
        assertThat(r.nutrients()).hasSize(TodayNutritionService.BASE_NUTRIENTS.size());
    }

    @Test
    @DisplayName("없는 사용자면 USER_NOT_FOUND")
    void unknownUser() {
        assertThatThrownBy(() -> service.getTodayNutrition(999_999L, NOV_3)).isInstanceOf(BusinessException.class);
    }

    private static Map<String, Double> amountsOf(TodayNutritionResponse r, long scheduleId) {
        return r.schedules().stream().filter(s -> s.scheduleId() == scheduleId).findFirst().orElseThrow().amounts();
    }
}
