package com.ucamp.app.domain.notification.controller;

import com.ucamp.app.domain.notification.dto.NotificationResponse;
import com.ucamp.app.domain.notification.dto.UnreadCountResponse;
import com.ucamp.app.domain.notification.entity.NotificationCategory;
import com.ucamp.app.domain.notification.service.NotificationService;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// TODO: 로그인 연동 후 X-User-Id 헤더 대신 로그인한 사용자 정보에서 userId를 꺼내도록 변경
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notificationService;

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
}
