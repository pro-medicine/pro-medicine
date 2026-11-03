package com.ucamp.app.domain.notification.event;

/**
 * 안부지킴이 신청이 수락됐을 때 발행하는 이벤트. (GuardianService.accept, 거절은 알림 없음)
 *
 * @param settingId      안부지킴이 설정 id
 * @param guardianUserId 신청한 사람 (알림을 받는 사람)
 * @param targetUserId   수락한 사람
 * @param inactivityDays 며칠 이상 접속하지 않으면 지킴이에게 알릴지
 */
public record GuardianAcceptedEvent(Long settingId, Long guardianUserId, Long targetUserId, int inactivityDays) {
}
