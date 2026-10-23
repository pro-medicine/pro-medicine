package com.ucamp.app.domain.friend.dto;

import com.ucamp.app.domain.user.entity.User;

public record FriendDetailResponse(
        Long userId,
        String nickname
) {
    public static FriendDetailResponse from(User friend) {
        return new FriendDetailResponse(friend.getId(), friend.getNickname());
    }
}
