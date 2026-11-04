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
 * @param warnings         오늘 먹는 영양제 성분 + 설문 조건으로 걸린 "함께 먹을 때 주의할 점" (강한 순)
 */
public record TodayNutritionResponse(
        LocalDate date,
        List<NutrientInfo> nutrients,
        List<ScheduleNutrients> schedules,
        int unsupportedCount,
        boolean sample,
        List<IntakeWarning> warnings
) {
    /** 성분 하나와 하루 권장량 (성별 기준, 모르면 남성 값) */
    public record NutrientInfo(String code, String name, String unit, double recommended) {
    }

    /**
     * 함께 먹을 때 주의할 점 1개 (recommendation_rules의 규칙 1개).
     *
     * @param ruleId    규칙 id (예: R-21)
     * @param kind      EXCLUDE(피하세요) · WARN(주의) · TIMING(시간 간격)
     * @param message   보여줄 문구 (규칙의 message_ko)
     * @param pair      짝 표시 (예: ["철", "칼슘"], ["오메가3", "와파린"], ["비타민 A", "임신·수유"])
     * @param nutrients 관련된 성분 코드 (화면의 성분 타일에 표시할 때 사용, 예: ["IRON", "CALCIUM"])
     * @param note      덧붙이는 설명 (예: "지금 둘 다 아침에 드시고 있어요"), 없으면 null
     */
    public record IntakeWarning(String ruleId, String kind, String message, List<String> pair,
                                List<String> nutrients, String note) {
    }

    /** 일정 한 번 먹을 때 들어오는 성분 함량 (성분 코드 → 양). 같은 영양제를 하루 여러 번 먹으면 하루 함량을 나눈 값 */
    public record ScheduleNutrients(long scheduleId, Map<String, Double> amounts) {
    }
}
