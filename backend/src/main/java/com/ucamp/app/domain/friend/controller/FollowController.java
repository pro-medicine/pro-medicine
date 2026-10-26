package com.ucamp.app.domain.friend.controller;

import com.ucamp.app.domain.friend.dto.FollowCreateRequest;
import com.ucamp.app.domain.friend.dto.FollowRespondRequest;
import com.ucamp.app.domain.friend.dto.FollowResponse;
import com.ucamp.app.domain.friend.enums.FollowStatus;
import com.ucamp.app.domain.friend.service.FollowService;
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

    // TODO: 로그인한 User 정보 반영해 수정
    @PostMapping
    public ResponseEntity<ApiResponse<FollowResponse>> request(
            @RequestParam Long userId,
            @Valid @RequestBody FollowCreateRequest request) {
        FollowResponse response = followService.request(userId, request.nickname());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<List<FollowResponse>>> getRequests(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "received") String direction,
            @RequestParam(defaultValue = "PENDING") FollowStatus status) {
        List<FollowResponse> response = followService.getRequests(userId, direction, status);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{followId}")
    public ResponseEntity<ApiResponse<FollowResponse>> respond(
            @RequestParam Long userId,
            @PathVariable Long followId,
            @Valid @RequestBody FollowRespondRequest request) {
        FollowResponse response = followService.respond(userId, followId, request.status());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{followId}")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @RequestParam Long userId,
            @PathVariable Long followId) {
        followService.cancel(userId, followId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }



}
