package com.ucamp.app.domain.friend.dto;

import com.ucamp.app.domain.user.entity.User;

public record FriendResponse(
        Long followId,
        Long userId,
        String nickname,
        boolean mutual
) {
    public static FriendResponse of(Long followId, User friend, boolean mutual) {
        return new FriendResponse(followId, friend.getId(), friend.getNickname(), mutual);
    }
}
