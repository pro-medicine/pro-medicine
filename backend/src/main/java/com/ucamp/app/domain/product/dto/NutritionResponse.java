package com.ucamp.app.domain.product.dto;

import java.util.List;

// 나의 영양관리 (성분별 섭취 현황)
// sample이 true면 예시 함량 데이터로 계산한 값
public record NutritionResponse(
        Long userId,
        List<NutrientIntakeResponse> nutrients,
        boolean sample
) {
}
