package com.ucamp.app.domain.medicine.dto;

public record MedicineSummary(
        Long medicineId,
        String name,
        String kind
) {
}
