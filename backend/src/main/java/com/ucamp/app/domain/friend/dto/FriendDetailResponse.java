package com.ucamp.app.domain.friend.dto;

import com.ucamp.app.domain.intake.dto.IntakeStatsResponse;
import com.ucamp.app.domain.product.dto.MySupplementResponse;
import com.ucamp.app.domain.user.entity.User;

import java.util.List;

public record FriendDetailResponse(
        Long userId,
        String nickname,
        boolean shared,
        int streak,
        int monthlyRate,
        int supplementCount,
        List<MySupplementResponse> supplements
) {
    public static FriendDetailResponse of(
            User friend,
            IntakeStatsResponse stats,
            List<MySupplementResponse> supplements) {
        return new FriendDetailResponse(
                friend.getId(),
                friend.getNickname(),
                true,
                stats.streakDays(),
                stats.monthlyRate(),
                supplements.size(),
                supplements);
    }

    public static FriendDetailResponse hidden(User friend) {
        return new FriendDetailResponse(friend.getId(), friend.getNickname(), false, 0, 0, 0, List.of());
    }
}
