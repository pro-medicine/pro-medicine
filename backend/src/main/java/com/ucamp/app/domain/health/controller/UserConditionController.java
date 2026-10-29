
package com.ucamp.app.domain.health.controller;

import com.ucamp.app.domain.health.dto.UserConditionsRequest;
import com.ucamp.app.domain.health.dto.UserConditionsResponse;
import com.ucamp.app.domain.health.service.UserConditionService;
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
@RequestMapping("/api/v1/users/{userId}/conditions")
public class UserConditionController {

    private final UserConditionService userConditionService;

    // 본인 질환정보 조회
    @GetMapping
    public ResponseEntity<ApiResponse<UserConditionsResponse>> getConditions(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        requireSelf(userId, jwt);

        return ResponseEntity.ok(
                ApiResponse.success(userConditionService.getConditions(userId))
        );
    }

    // 본인 질환정보 수정
    @PutMapping
    public ResponseEntity<ApiResponse<UserConditionsResponse>> replaceConditions(
            @PathVariable Long userId,
            @RequestBody UserConditionsRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        requireSelf(userId, jwt);

        return ResponseEntity.ok(
                ApiResponse.success(
                        userConditionService.replaceConditions(userId, request)
                )
        );
    }

    // 로그인 사용자와 요청 대상 사용자 비교
    private void requireSelf(Long requestedUserId, Jwt jwt) {
        Long authenticatedUserId = Long.valueOf(jwt.getSubject());

        if (!requestedUserId.equals(authenticatedUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
