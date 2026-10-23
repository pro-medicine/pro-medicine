package com.ucamp.app.domain.intake.dto;

public record IntakeStatsResponse(
        Long userId,
        int streakDays,
        long activeProductCount,
        int monthlyRate
) {
}
