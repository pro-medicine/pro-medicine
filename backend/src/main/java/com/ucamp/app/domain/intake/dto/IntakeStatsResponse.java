package com.ucamp.app.domain.intake.dto;

import java.util.List;

public record IntakeStatsResponse(
        Long userId,
        int streakDays,
        long activeProductCount,
        int monthlyRate,
        int bestStreakDays,          // 최근 1년 중 가장 길었던 연속 복용 일수 (현재 연속 포함)
        int weeklyRate,              // 이번 주 월요일 ~ 오늘 달성률
        List<DayProgress> weekDays,  // 이번 주 월~일 7개 (오늘 이후는 expected·taken 0)
        List<WeekRate> monthWeeks    // 이번 달 주차별 달성률
) {
}
