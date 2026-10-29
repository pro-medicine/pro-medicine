
package com.ucamp.app.domain.product.controller;

import com.ucamp.app.domain.product.dto.NutritionResponse;
import com.ucamp.app.domain.product.service.NutritionService;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/{userId}/nutrition")
public class NutritionController {

    private final NutritionService nutritionService;

    // 로그인한 사용자의 영양정보만 조회
    @GetMapping
    public ResponseEntity<ApiResponse<NutritionResponse>> getNutrition(
            @PathVariable Long userId,
            @CurrentUserId Long currentUserId
    ) {
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        if (!userId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return ResponseEntity.ok(
                ApiResponse.success(nutritionService.getNutrition(userId))
        );
    }
}
