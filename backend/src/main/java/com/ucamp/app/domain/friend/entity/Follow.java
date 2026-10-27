package com.ucamp.app.domain.friend.entity;

import com.ucamp.app.domain.friend.enums.FollowStatus;
import com.ucamp.app.global.common.Timestamped;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "follows",
uniqueConstraints = @UniqueConstraint(
        name = "uq_follow_pair",
        columnNames = {"follower_id", "following_id"}
))
public class Follow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long followId;

    @Column(nullable = false)
    private Long followerId;

    @Column(nullable = false)
    private Long followingId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private FollowStatus status;

    private Follow(Long followerId, Long followingId) {
        this.followerId = followerId;
        this.followingId = followingId;
        this.status = FollowStatus.PENDING;
    }

    public static Follow request(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new BusinessException(ErrorCode.INVALID_FOLLOW);
        }
        return new Follow(followerId, followingId);
    }

    public void accept() {
        requirePending();
        this.status = FollowStatus.ACCEPTED;
    }

    public void reject() {
        requirePending();
        this.status = FollowStatus.REJECTED;
    }

    private void requirePending() {
        if (this.status != FollowStatus.PENDING) {
            throw new BusinessException(ErrorCode.COMPLETED_FOLLOW_REQUEST);
        }
    }
}
