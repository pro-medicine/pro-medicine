package com.ucamp.app.domain.friend.repository;

import com.ucamp.app.domain.friend.entity.Follow;
import com.ucamp.app.domain.friend.enums.FollowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {
    /** 같은 방향의 팔로우가 이미 있는지 확인 (요청 중복 방지) */
    Optional<Follow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    /** 내가 받은 요청 (following_id = 나) */
    List<Follow> findByFollowingIdAndStatusOrderByFollowIdDesc(Long followingId, FollowStatus status);

    /** 내가 보낸 요청 (follower_id = 나) */
    List<Follow> findByFollowerIdAndStatusOrderByFollowIdDesc(Long followerId, FollowStatus status);

    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    boolean existsByFollowerIdAndFollowingIdAndStatus(Long userId, Long friendUserId, FollowStatus followStatus);
}
