package com.ucamp.app.domain.survey.dto;

import java.math.BigDecimal;

// 설문 Q-SUPPLEMENTS 한 항목: 현재 복용 중인 영양제 성분과 하루 함량. ingredient는 IngredientCode 이름
public record SupplementIntake(String ingredient, BigDecimal amount, String unit) {
}
