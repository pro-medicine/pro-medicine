package com.ucamp.app.domain.notification.event;

/**
 * 안부지킴이 신청이 저장됐을 때 발행하는 이벤트. (GuardianService.request)
 *
 * @param settingId      안부지킴이 설정 id
 * @param guardianUserId 신청한 사람 (지킴이가 되려는 사람)
 * @param targetUserId   신청을 받은 사람 (알림을 받는 사람, 수락·거절함)
 */
public record GuardianRequestedEvent(Long settingId, Long guardianUserId, Long targetUserId) {
}
