package com.ucamp.app.domain.friend.dto;

import com.ucamp.app.domain.intake.dto.DayProgress;
import com.ucamp.app.domain.intake.dto.IntakeStatsResponse;
import com.ucamp.app.domain.intake.dto.WeekRate;
import com.ucamp.app.domain.product.dto.MySupplementResponse;
import com.ucamp.app.domain.user.entity.User;

import java.util.List;

public record FriendDetailResponse(
        Long userId,
        String nickname,
        int streak,
        int monthlyRate,
        int supplementCount,
        List<MySupplementResponse> supplements,
        // 마이페이지와 같은 통계 그래프용 (IntakeStatsResponse 값을 그대로 전달)
        int bestStreakDays,
        int weeklyRate,
        List<DayProgress> weekDays,
        List<WeekRate> monthWeeks
) {
    public static FriendDetailResponse of(
            User friend,
            IntakeStatsResponse stats,
            List<MySupplementResponse> supplements) {
        return new FriendDetailResponse(
                friend.getId(),
                friend.getNickname(),
                stats.streakDays(),
                stats.monthlyRate(),
                supplements.size(),
                supplements,
                stats.bestStreakDays(),
                stats.weeklyRate(),
                stats.weekDays(),
                stats.monthWeeks());
    }
}
