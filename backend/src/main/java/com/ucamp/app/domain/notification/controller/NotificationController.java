package com.ucamp.app.domain.notification.controller;

import com.ucamp.app.domain.notification.dto.NotificationResponse;
import com.ucamp.app.domain.notification.dto.UnreadCountResponse;
import com.ucamp.app.domain.notification.entity.NotificationCategory;
import com.ucamp.app.domain.notification.service.NotificationService;
import com.ucamp.app.domain.notification.service.NotificationSseService;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

// 사용자 식별: JWT Bearer 토큰에서 @CurrentUserId로 로그인 사용자 ID를 가져온다.
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final NotificationSseService notificationSseService;

    // 알림 목록 (category 없으면 전체, INTAKE = 내 복용, FRIEND = 친구 소식)
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @CurrentUserId Long userId,
            @RequestParam(required = false) NotificationCategory category) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getNotifications(requireLogin(userId), category)));
    }

    // 안 읽은 알림 개수
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getUnreadCount(requireLogin(userId))));
    }

    // 알림 하나 읽음
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @CurrentUserId Long userId,
            @PathVariable Long notificationId) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.markRead(requireLogin(userId), notificationId)));
    }

    // 모두 읽음
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @CurrentUserId Long userId) {
        notificationService.markAllRead(requireLogin(userId));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // 실시간 알림 구독 (SSE): JWT Bearer 헤더가 포함된 fetch 기반 SSE 클라이언트를 사용한다.
    // 새 알림이 생기면 "notification" 이벤트로 신호를 보낸다.
    @GetMapping(
            value = "/subscribe",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter subscribe(@CurrentUserId Long userId) {
        return notificationSseService.subscribe(requireLogin(userId));
    }

    // 인증된 사용자 ID가 없으면 401
    private Long requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
