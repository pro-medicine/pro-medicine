package com.ucamp.app.domain.notification.event;

/**
 * 팔로우 요청이 저장됐을 때 발행하는 이벤트.
 *
 * @param followerId  요청한 사람
 * @param followingId 요청을 받은 사람 (알림을 받는 사람)
 */
public record FollowRequestedEvent(Long followerId, Long followingId) {
}
