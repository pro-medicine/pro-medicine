package com.ucamp.app.domain.friend.dto;

import com.ucamp.app.domain.friend.entity.Follow;
import com.ucamp.app.domain.friend.enums.FollowStatus;

public record FollowResponse(
        Long followId,
        Long followerId,
        String followerNickname,
        Long followingId,
        String followingNickname,
        FollowStatus status
) {
    public static FollowResponse of(Follow follow,
                                    String followerNickname,
                                    String followingNickname) {
        return new FollowResponse(
                follow.getFollowId(),
                follow.getFollowerId(),
                followerNickname,
                follow.getFollowingId(),
                followingNickname,
                follow.getStatus()
        );
    }
}