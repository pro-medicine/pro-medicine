
package com.ucamp.app.domain.health.controller;

import com.ucamp.app.domain.health.dto.HealthProfileRequest;
import com.ucamp.app.domain.health.dto.HealthProfileResponse;
import com.ucamp.app.domain.health.service.HealthProfileService;
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
@RequestMapping("/api/v1/users/{userId}/health-profile")
public class HealthProfileController {

    private final HealthProfileService healthProfileService;

    // 본인 건강정보 조회
    @GetMapping
    public ResponseEntity<ApiResponse<HealthProfileResponse>> getHealthProfile(
            @PathVariable Long userId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        requireSelf(userId, jwt);

        return ResponseEntity.ok(
                ApiResponse.success(
                        healthProfileService.getHealthProfile(userId)
                )
        );
    }

    // 본인 건강정보 수정
    @PutMapping
    public ResponseEntity<ApiResponse<HealthProfileResponse>> saveHealthProfile(
            @PathVariable Long userId,
            @RequestBody HealthProfileRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        requireSelf(userId, jwt);

        return ResponseEntity.ok(
                ApiResponse.success(
                        healthProfileService.saveHealthProfile(userId, request)
                )
        );
    }

    // JWT 사용자 ID와 요청 사용자 ID 비교
    private void requireSelf(Long requestedUserId, Jwt jwt) {
        Long authenticatedUserId = Long.valueOf(jwt.getSubject());

        if (!requestedUserId.equals(authenticatedUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
