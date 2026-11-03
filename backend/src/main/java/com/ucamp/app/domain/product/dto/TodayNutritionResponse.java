package com.ucamp.app.domain.product.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 홈 "오늘 먹은 영양소". 먹은 양 합계는 화면에서 복용 체크 상태(taken)로 계산한다
 * → 체크하는 순간 서버 응답을 기다리지 않고 그래프가 올라간다.
 *
 * @param nutrients        보여줄 성분 (대표 성분 + 오늘 영양제에 든 성분). 아무것도 안 먹어도 0%로 보여준다
 * @param schedules        오늘 복용 일정별 성분 함량 (홈 타임라인의 id = scheduleId)
 * @param unsupportedCount 함량 정보가 없어 계산에서 빠진 영양제 일정 수 (직접 입력 등)
 * @param sample           예시 함량 데이터로 계산한 값이 있는지
 */
public record TodayNutritionResponse(
        LocalDate date,
        List<NutrientInfo> nutrients,
        List<ScheduleNutrients> schedules,
        int unsupportedCount,
        boolean sample
) {
    /** 성분 하나와 하루 권장량 (성별 기준, 모르면 남성 값) */
    public record NutrientInfo(String code, String name, String unit, double recommended) {
    }

    /** 일정 한 번 먹을 때 들어오는 성분 함량 (성분 코드 → 양). 같은 영양제를 하루 여러 번 먹으면 하루 함량을 나눈 값 */
    public record ScheduleNutrients(long scheduleId, Map<String, Double> amounts) {
    }
}
