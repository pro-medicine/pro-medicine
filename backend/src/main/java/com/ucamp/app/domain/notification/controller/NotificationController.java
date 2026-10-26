package com.ucamp.app.domain.notification.controller;

import com.ucamp.app.domain.notification.dto.NotificationResponse;
import com.ucamp.app.domain.notification.dto.UnreadCountResponse;
import com.ucamp.app.domain.notification.entity.NotificationCategory;
import com.ucamp.app.domain.notification.service.NotificationService;
import com.ucamp.app.domain.notification.service.NotificationSseService;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

// TODO: 로그인 연동 후 X-User-Id 헤더 대신 로그인한 사용자 정보에서 userId를 꺼내도록 변경
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final NotificationSseService notificationSseService;

    // 알림 목록 (category 없으면 전체, INTAKE = 내 복용, FRIEND = 친구 소식)
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) NotificationCategory category) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getNotifications(userId, category)));
    }

    // 안 읽은 알림 개수
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getUnreadCount(userId)));
    }

    // 알림 하나 읽음
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long notificationId) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.markRead(userId, notificationId)));
    }

    // 모두 읽음
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @RequestHeader("X-User-Id") Long userId) {
        notificationService.markAllRead(userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // 실시간 알림 구독 (SSE): 새 알림이 생기면 "notification" 이벤트로 신호를 보낸다.
    // 브라우저 EventSource는 헤더를 붙일 수 없어서, 로그인 연동 전까지 userId를 쿼리 파라미터로 받는다.
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam Long userId) {
        return notificationSseService.subscribe(userId);
    }
}
