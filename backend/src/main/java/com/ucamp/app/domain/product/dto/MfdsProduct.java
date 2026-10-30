package com.ucamp.app.domain.product.dto;

public record MfdsProduct(
        String itemNo,
        String productName,
        String manufacturer,
        String mainFunction,
        String intakeMethod,
        String intakeCaution,
        String baseStandard
) {
}
