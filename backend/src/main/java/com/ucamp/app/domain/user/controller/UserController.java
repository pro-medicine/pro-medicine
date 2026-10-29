
package com.ucamp.app.domain.user.controller;

import com.ucamp.app.domain.user.dto.NicknameUpdateRequest;
import com.ucamp.app.domain.user.dto.UserResponse;
import com.ucamp.app.domain.user.service.UserService;
import com.ucamp.app.global.common.ApiResponse;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    // 사용자 정보 조회 - 본인만 허용
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        requireSelf(userId, jwt);

        return ResponseEntity.ok(
                ApiResponse.success(userService.getUser(userId))
        );
    }

    // 닉네임 변경 - 본인만 허용
    @PatchMapping("/{userId}/nickname")
    public ResponseEntity<ApiResponse<UserResponse>> changeNickname(
            @PathVariable Long userId,
            @RequestBody NicknameUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        requireSelf(userId, jwt);

        return ResponseEntity.ok(
                ApiResponse.success(
                        userService.changeNickname(userId, request.nickname())
                )
        );
    }

    // JWT 인증 사용자와 요청한 사용자 ID 비교
    private void requireSelf(Long requestedUserId, Jwt jwt) {
        Long authenticatedUserId = Long.valueOf(jwt.getSubject());

        if (!requestedUserId.equals(authenticatedUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
