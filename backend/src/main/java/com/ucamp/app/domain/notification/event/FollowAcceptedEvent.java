package com.ucamp.app.domain.notification.event;

/**
 * 팔로우 요청이 수락됐을 때 발행하는 이벤트. (거절은 알림을 보내지 않으므로 이벤트도 없다)
 *
 * @param followerId  요청한 사람 (알림을 받는 사람)
 * @param followingId 요청을 수락한 사람
 */
public record FollowAcceptedEvent(Long followerId, Long followingId) {
}
