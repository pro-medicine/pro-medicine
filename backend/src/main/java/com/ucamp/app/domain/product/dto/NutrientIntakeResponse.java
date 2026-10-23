package com.ucamp.app.domain.product.dto;

// 성분 하나의 하루 섭취량과 권장량 대비 섭취율
// percent는 100을 넘을 수 있음 (게이지는 화면에서 100%까지만 채움)
public record NutrientIntakeResponse(
        String name,
        String unit,
        int intakeAmount,
        int recommendedAmount,
        int percent
) {
}
