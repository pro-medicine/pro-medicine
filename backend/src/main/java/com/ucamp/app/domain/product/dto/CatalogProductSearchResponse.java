package com.ucamp.app.domain.product.dto;

import com.ucamp.app.domain.product.entity.CatalogProduct;

// 등록 폼 제품 검색 결과 1개
public record CatalogProductSearchResponse(
        Long catalogProductId,
        String productName,
        String manufacturer,    // 업소명 (없을 수 있음)
        String mainFunction,    // 식약처 주된기능성
        String intakeMethod     // 식약처 섭취방법 (예: 1일 1회, 1회 1정)
) {
    public static CatalogProductSearchResponse from(CatalogProduct product) {
        return new CatalogProductSearchResponse(
                product.getCatalogProductId(),
                product.getProductName(),
                product.getManufacturer(),
                product.getMainFunction(),
                product.getIntakeMethod()
        );
    }
}