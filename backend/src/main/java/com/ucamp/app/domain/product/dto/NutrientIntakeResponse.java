package com.ucamp.app.domain.product.dto;

// 섭취량·권장량은 소수 가능 (예: 비타민 B6 1.5mg)
// percent는 100을 넘을 수 있음 (게이지는 화면에서 100%까지만 채움)
public record NutrientIntakeResponse(
        String name,
        String unit,
        double intakeAmount,
        double recommendedAmount,
        int percent
) {
}
