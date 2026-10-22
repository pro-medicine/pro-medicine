package com.ucamp.app.domain.recommendation.pipeline;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * 추천 성분 한 개.
 *
 * @param ingredient     V0는 LLM이 준 이름 원문, V1부터는 성분 코드
 * @param amount         하루 추가 섭취량 (V0·V1은 LLM 값, V2부터 기준표 계산). 없으면 null
 * @param currentIntake  계산에 쓴 현재 섭취량
 * @param intakeStatus   KNOWN | UNKNOWN
 * @param excluded       규칙으로 제외됨. 제외돼도 목록에 남겨 LLM 원래 출력을 보존한다
 */
@Builder(toBuilder = true)
public record RecommendationItem(
        String ingredient,
        String reason,
        int sortOrder,
        BigDecimal amount,
        String unit,
        BigDecimal currentIntake,
        String intakeStatus,
        String doseNote,
        boolean excluded,
        List<String> appliedRuleIds,
        List<String> warnings
) {
    public List<String> appliedRuleIdsOrEmpty() {
        return appliedRuleIds == null ? List.of() : appliedRuleIds;
    }

    public List<String> warningsOrEmpty() {
        return warnings == null ? List.of() : warnings;
    }
}
