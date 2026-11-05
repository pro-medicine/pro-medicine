package com.ucamp.app.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record WithdrawalRequest(@NotBlank String password) {
}
