package com.ucamp.app.domain.health.dto;

import com.ucamp.app.domain.health.entity.IngredientCode;

public record IngredientResponse(String code, String name, String unit) {
    public static IngredientResponse from(IngredientCode code) {
        return new IngredientResponse(code.name(), code.getNameKo(), code.getUnit());
    }
}
