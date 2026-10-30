package com.ucamp.app.domain.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).{8,72}$") String password,
        @NotBlank @Size(max = 50) String name,
        @NotNull @Min(1) @Max(120) Integer age,
        @NotBlank String gender,
        @AssertTrue boolean termsAgreed
) {
}
