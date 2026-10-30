package com.ucamp.app.domain.notification.controller;

import com.ucamp.app.domain.notification.dto.IntakeSummaryResponse;
import com.ucamp.app.domain.notification.service.IntakeSummaryService;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 홈 복용 알림 카드 API.
 * NotificationController와 같은 주소(/api/v1/notifications) 아래에 두지만,
 * 알림 목록·SSE와는 역할이 달라서 컨트롤러를 나눴다. (기존 컨트롤러 테스트에 영향 없음)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class IntakeSummaryController {

    private final IntakeSummaryService intakeSummaryService;

    // 다음 복용 알림 + 이번 주 제시간 복용률
    @GetMapping("/intake-summary")
    public ResponseEntity<ApiResponse<IntakeSummaryResponse>> getIntakeSummary(@CurrentUserId Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);   // 사용자를 알 수 없으면 401
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())   // 복용 기록(건강 정보)이라 저장하지 않음
                .body(ApiResponse.success(intakeSummaryService.getSummary(userId)));
    }
}
