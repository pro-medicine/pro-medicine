package com.ucamp.app.domain.user.dto;

import jakarta.validation.constraints.NotNull;

public record ShareIntakeRequest(
        @NotNull Boolean shared
) {
}
