package com.ucamp.app.domain.guardian.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record GuardianDaysRequest(
        @Min(1) @Max(30) int inactivityDays
) {
}
