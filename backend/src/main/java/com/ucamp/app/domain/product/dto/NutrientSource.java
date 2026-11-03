package com.ucamp.app.domain.product.dto;

public record NutrientSource(
        Long userProductId,
        String productName,
        double amount
) {

}
