package com.ucamp.app.domain.friend.dto;

import com.ucamp.app.domain.friend.enums.FollowStatus;
import com.ucamp.app.domain.user.entity.User;

public record FollowerResponse(
        Long followId,
        Long userId,
        String nickname,
        FollowStatus followBackStatus
) {
    public static FollowerResponse of(Long followId, User user, FollowStatus followBackStatus) {
        return new FollowerResponse(followId, user.getId(), user.getNickname(), followBackStatus);
    }
}
