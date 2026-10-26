package com.ucamp.app.domain.guardian.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record GuardianCreteRequest(
        @NotBlank String nickname,
        @Min(1) @Max(30) int inactivityDays
) {
}
