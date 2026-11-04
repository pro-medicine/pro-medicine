package com.ucamp.app.domain.product.controller;

import com.ucamp.app.domain.product.dto.TodayNutritionResponse;
import com.ucamp.app.domain.product.service.TodayNutritionService;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

/** 홈 "오늘 먹은 영양소": GET /api/v1/users/{userId}/nutrition/today?date=2026-11-03 (date 없으면 오늘, 한국 시간) */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/{userId}/nutrition/today")
public class TodayNutritionController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TodayNutritionService todayNutritionService;

    // 로그인한 사용자 본인 것만 (마이페이지 영양관리 API와 같은 규칙)
    @GetMapping
    public ResponseEntity<ApiResponse<TodayNutritionResponse>> getTodayNutrition(
            @PathVariable Long userId,
            @CurrentUserId Long currentUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!userId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        LocalDate target = date != null ? date : LocalDate.now(KST);
        return ResponseEntity.ok(ApiResponse.success(todayNutritionService.getTodayNutrition(userId, target)));
    }
}
