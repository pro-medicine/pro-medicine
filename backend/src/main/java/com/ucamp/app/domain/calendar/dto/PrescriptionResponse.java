package com.ucamp.app.domain.calendar.dto;

import com.ucamp.app.domain.calendar.entity.UserPrescription;

import java.time.LocalDate;

public record PrescriptionResponse(
        Long prescriptionId,
        String name,
        LocalDate startAt,
        LocalDate endAt,
        boolean isActive,
        boolean isShown
) {
    public static PrescriptionResponse from(UserPrescription prescription) {
        return new PrescriptionResponse(
                prescription.getPrescriptionId(),
                prescription.getName(),
                prescription.getStartAt(),
                prescription.getEndAt(),
                prescription.isActive(),
                prescription.isShown()
        );
    }
}
