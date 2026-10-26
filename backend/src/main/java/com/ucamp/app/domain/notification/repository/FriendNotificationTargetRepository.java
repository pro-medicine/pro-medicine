package com.ucamp.app.domain.notification.repository;

import com.ucamp.app.domain.friend.entity.Follow;
import com.ucamp.app.domain.friend.enums.FollowStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 친구 알림을 받을 사람을 찾는 읽기 전용 조회.
 * follows 테이블은 친구 기능(friend 도메인) 담당이므로, 그쪽 Repository를 수정하지 않고 알림 쪽에 따로 둔다.
 */
public interface FriendNotificationTargetRepository extends Repository<Follow, Long> {

    /**
     * userId를 팔로우하고 있는 사람들의 id. (친구 복용 완료 알림을 받을 사람)
     * - 팔로우가 수락된 상태 (ACCEPTED)
     * - 팔로우를 취소하지 않음 (deleted_at IS NULL)
     */
    @Query("""
            SELECT f.followerId FROM Follow f
            WHERE f.followingId = :userId
              AND f.status = :status
              AND f.deletedAt IS NULL
            ORDER BY f.followerId
            """)
    List<Long> findFollowerIds(@Param("userId") Long userId, @Param("status") FollowStatus status);

    default List<Long> findAcceptedFollowerIds(Long userId) {
        return findFollowerIds(userId, FollowStatus.ACCEPTED);
    }
}
