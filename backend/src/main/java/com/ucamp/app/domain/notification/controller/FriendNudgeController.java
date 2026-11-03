package com.ucamp.app.domain.notification.controller;

import com.ucamp.app.domain.notification.dto.NudgeRequest;
import com.ucamp.app.domain.notification.service.FriendNudgeService;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 친구 챙겨주기: POST /api/v1/notifications/nudges  { "targetUserId": 2 }
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications/nudges")
public class FriendNudgeController {

    private final FriendNudgeService friendNudgeService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> nudge(
            @CurrentUserId Long userId,
            @Valid @RequestBody NudgeRequest request) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        friendNudgeService.nudge(userId, request.targetUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }
}
