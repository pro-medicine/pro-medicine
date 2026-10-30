package com.ucamp.app.domain.friend.dto;

import com.ucamp.app.domain.user.entity.User;

public record FriendResponse(
        Long followId,
        Long userId,
        String nickname,
        boolean mutual,
        int streakDays
) {
    public static FriendResponse of(Long followId, User friend, boolean mutual, int streakDays) {
        return new FriendResponse(followId, friend.getId(), friend.getNickname(), mutual, streakDays);
    }
}
