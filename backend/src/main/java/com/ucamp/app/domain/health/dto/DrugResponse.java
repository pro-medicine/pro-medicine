package com.ucamp.app.domain.health.dto;

import com.ucamp.app.domain.health.entity.Drug;

public record DrugResponse(String drugId, String name, String drugClass) {
    public static DrugResponse from(Drug drug) {
        return new DrugResponse(drug.getDrugId(), drug.getName(), drug.getDrugClass());
    }
}
