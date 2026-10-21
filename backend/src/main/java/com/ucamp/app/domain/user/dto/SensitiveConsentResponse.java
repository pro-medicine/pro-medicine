package com.ucamp.app.domain.user.dto;

import com.ucamp.app.domain.user.entity.User;

import java.time.LocalDateTime;

public record SensitiveConsentResponse(
        boolean sensitiveConsent,
        LocalDateTime sensitiveConsentAt
) {
    public static SensitiveConsentResponse from(User user) {
        return new SensitiveConsentResponse(user.isSensitiveConsent(), user.getSensitiveConsentAt());
    }
}
