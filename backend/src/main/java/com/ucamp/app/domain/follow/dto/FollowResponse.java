package com.ucamp.app.domain.follow.dto;

import com.ucamp.app.domain.follow.entity.Follow;
import com.ucamp.app.domain.follow.enums.FollowStatus;

public record FollowResponse(
        Long followId,
        Long followerId,
        Long followingId,
        FollowStatus status
) {
    public static FollowResponse from(Follow follow) {
        return new FollowResponse(
                follow.getFollowId(),
                follow.getFollowerId(),
                follow.getFollowingId(),
                follow.getStatus()
        );
    }
}
