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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

// 사용자 식별: 팀 공통 @CurrentUserId (로그인 연동 전에는 X-User-Id 헤더, 연동 후에는 로그인 사용자로 CurrentUserResolver만 교체)
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

    // 실시간 알림 구독 (SSE): 새 알림이 생기면 "notification" 이벤트로 신호를 보낸다.
    // 브라우저 EventSource는 헤더를 붙일 수 없어서, 로그인 연동 전까지 userId를 쿼리 파라미터로 받는다.
    // TODO: 로그인 연동 시 인증 방식 결정 (예: 구독 주소만 쿼리로 토큰 전달)
    @GetMapping(
            value = "/subscribe",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter subscribe(@AuthenticationPrincipal Jwt jwt) {

        Long userId = Long.valueOf(jwt.getSubject());

        return notificationSseService.subscribe(userId);
    }

    // 사용자를 알 수 없으면(헤더 없음, 비로그인) 401
    private Long requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
