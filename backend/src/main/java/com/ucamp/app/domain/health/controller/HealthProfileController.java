
package com.ucamp.app.domain.health.controller;

import com.ucamp.app.domain.health.dto.HealthProfileRequest;
import com.ucamp.app.domain.health.dto.HealthProfileResponse;
import com.ucamp.app.domain.health.service.HealthProfileService;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/health-profile")
public class HealthProfileController {

    private final HealthProfileService healthProfileService;

    // 본인 건강정보 조회
    @GetMapping
    public ResponseEntity<ApiResponse<HealthProfileResponse>> getHealthProfile(
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        healthProfileService.getHealthProfile(userId)
                )
        );
    }

    // 본인 건강정보 수정
    @PutMapping
    public ResponseEntity<ApiResponse<HealthProfileResponse>> saveHealthProfile(
            @RequestBody HealthProfileRequest request,
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        healthProfileService.saveHealthProfile(userId, request)
                )
        );
    }
}
