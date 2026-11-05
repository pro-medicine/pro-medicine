
package com.ucamp.app.domain.health.controller;

import com.ucamp.app.domain.health.dto.UserConditionsRequest;
import com.ucamp.app.domain.health.dto.UserConditionsResponse;
import com.ucamp.app.domain.health.service.UserConditionService;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/conditions")
public class UserConditionController {

    private final UserConditionService userConditionService;

    // 본인 질환정보 조회
    @GetMapping
    public ResponseEntity<ApiResponse<UserConditionsResponse>> getConditions(
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(userConditionService.getConditions(userId))
        );
    }

    // 본인 질환정보 수정
    @PutMapping
    public ResponseEntity<ApiResponse<UserConditionsResponse>> replaceConditions(
            @RequestBody UserConditionsRequest request,
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        userConditionService.replaceConditions(userId, request)
                )
        );
    }
}
