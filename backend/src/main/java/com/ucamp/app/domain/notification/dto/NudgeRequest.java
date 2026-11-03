package com.ucamp.app.domain.notification.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 친구 챙겨주기 요청
 *
 * @param targetUserId 챙겨줄 친구 (알림을 받는 사람)
 */
public record NudgeRequest(@NotNull Long targetUserId) {
}
