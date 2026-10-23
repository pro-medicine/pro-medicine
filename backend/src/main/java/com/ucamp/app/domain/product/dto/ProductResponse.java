package com.ucamp.app.domain.product.dto;

import com.ucamp.app.domain.product.entity.CatalogProduct;

// 영양제 검색 결과와 상세(영양정보)에 같이 사용
// 표기 규칙은 docs/specs/product-detail.md 참고
public record ProductResponse(
        Long productId,
        String productName,
        String manufacturer,
        String category,
        String ingredientSummary,
        String packageSize,
        Integer price,
        String mainFunction,
        String intakeMethod,
        String intakeCaution,
        String imageUrl,
        boolean sample
) {
    // mfds_item_no가 SAMPLE-로 시작하면 예시(목업) 데이터
    private static final String SAMPLE_PREFIX = "SAMPLE-";

    public static ProductResponse from(CatalogProduct product) {
        boolean sample = product.getMfdsItemNo() != null && product.getMfdsItemNo().startsWith(SAMPLE_PREFIX);
        return new ProductResponse(
                product.getCatalogProductId(),
                product.getProductName(),
                product.getManufacturer(),
                product.getCategory(),
                product.getIngredientSummary(),
                product.getPackageSize(),
                product.getPrice(),
                product.getMainFunction(),
                product.getIntakeMethod(),
                product.getIntakeCaution(),
                product.getImageUrl(),
                sample
        );
    }
}
