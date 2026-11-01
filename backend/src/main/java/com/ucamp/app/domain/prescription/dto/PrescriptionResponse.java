package com.ucamp.app.domain.prescription.dto;

import com.ucamp.app.domain.prescription.entity.Prescription;

import java.time.LocalDate;

public record PrescriptionResponse(
        Long prescriptionId,
        String name,
        LocalDate startAt,
        LocalDate endAt,
        int createdEventCount
) {
    public static PrescriptionResponse of(Prescription prescription, int createdEventCount) {
        return new PrescriptionResponse(
                prescription.getPrescriptionId(),
                prescription.getName(),
                prescription.getStartAt(),
                prescription.getEndAt(),
                createdEventCount
        );
    }
}
