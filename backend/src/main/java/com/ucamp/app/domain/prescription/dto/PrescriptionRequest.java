package com.ucamp.app.domain.prescription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record PrescriptionRequest(
        @NotBlank @Size(max = 150) String name,
        Long medicineId,
        @NotNull LocalDate startAt,
        LocalDate endAt,
        LocalTime scheduledTime,
        @Size(max = 1000) String notes
        ) {
}
