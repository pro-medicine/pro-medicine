package com.ucamp.app.domain.health.dto;

// 설문 Q-MEDICATIONS 한 항목. 검색에서 고르면 drugId, 못 찾으면 name
public record MedicationEntry(String drugId, String name) {
}
