package com.ucamp.app.domain.prescription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PrescriptionRequest(
        @NotBlank @Size(max = 30) String name,
        Long ingredientId,
        @NotNull LocalDate startAt,
        LocalDate endAt,
        @Size(max = 1000) String notes
        ) {
}
