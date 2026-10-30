
package com.ucamp.app.domain.friend.controller;

import com.ucamp.app.domain.friend.dto.FollowCreateRequest;
import com.ucamp.app.domain.friend.dto.FollowRespondRequest;
import com.ucamp.app.domain.friend.dto.FollowResponse;
import com.ucamp.app.domain.friend.enums.FollowStatus;
import com.ucamp.app.domain.friend.service.FollowService;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/follows")
public class FollowController {

    private final FollowService followService;

    // 로그인한 사용자로 팔로우 신청
    @PostMapping
    public ResponseEntity<ApiResponse<FollowResponse>> request(
            @CurrentUserId Long userId,
            @Valid @RequestBody FollowCreateRequest request) {

        FollowResponse response =
                followService.request(userId, request.nickname());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    // 로그인한 사용자의 팔로우 요청 목록 조회
    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<List<FollowResponse>>> getRequests(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "received") String direction,
            @RequestParam(defaultValue = "PENDING") FollowStatus status) {

        List<FollowResponse> response =
                followService.getRequests(userId, direction, status);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 팔로우 요청 수락 또는 거절
    @PatchMapping("/{followId}")
    public ResponseEntity<ApiResponse<FollowResponse>> respond(
            @CurrentUserId Long userId,
            @PathVariable Long followId,
            @Valid @RequestBody FollowRespondRequest request) {

        FollowResponse response =
                followService.respond(userId, followId, request.status());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 팔로우 요청 취소 또는 관계 삭제
    @DeleteMapping("/{followId}")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @CurrentUserId Long userId,
            @PathVariable Long followId) {

        followService.cancel(userId, followId);

        return ResponseEntity.noContent().build();
    }
}
