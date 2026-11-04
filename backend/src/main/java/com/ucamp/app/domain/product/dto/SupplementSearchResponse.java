package com.ucamp.app.domain.product.dto;

import com.ucamp.app.domain.product.entity.Supplement;

// 등록 폼 영양제 검색 결과 1개 (엑셀 영양제)
public record SupplementSearchResponse(
        Long supplementId,
        String productName,
        String manufacturer,
        String category,         // 중분류 (비타민 D 등)
        String intakeMethod,     // 섭취 방법 (예: 1일 2회)
        String nutrientSummary   // 하루 함량 요약 (예: 비타민 D 10㎍ · 아연 8.5mg), 없으면 null
) {
    public static SupplementSearchResponse from(Supplement s) {
        return new SupplementSearchResponse(
                s.getSupplementId(),
                s.getProductName(),
                s.getManufacturer(),
                s.getCategory(),
                s.intakeText(),
                s.getNutrientSummary()
        );
    }
}
