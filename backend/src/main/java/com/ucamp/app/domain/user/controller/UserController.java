package com.ucamp.app.domain.user.controller;

import com.ucamp.app.domain.user.dto.NicknameUpdateRequest;
import com.ucamp.app.domain.user.dto.UserResponse;
import com.ucamp.app.domain.user.service.UserService;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    // TODO: 로그인 연동 후 GET /api/v1/users/me 로 변경
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUser(userId)));
    }

    // TODO: 로그인 연동 후 PATCH /api/v1/users/me/nickname 으로 변경
    @PatchMapping("/{userId}/nickname")
    public ResponseEntity<ApiResponse<UserResponse>> changeNickname(@PathVariable Long userId,
                                                                    @RequestBody NicknameUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.changeNickname(userId, request.nickname())));
    }
}
