package com.ucamp.app.domain.health.dto;

// 저장된 복용 약. drugId가 없으면 마스터에 없는 약(unmatchedName)
public record MedicationInfo(String drugId, String drugName, String drugClass, String unmatchedName) {
}
