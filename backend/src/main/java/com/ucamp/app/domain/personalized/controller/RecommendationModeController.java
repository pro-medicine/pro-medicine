package com.ucamp.app.domain.personalized.controller;

import com.ucamp.app.domain.personalized.dto.RecommendationModeResponse;
import com.ucamp.app.domain.personalized.service.RecommendationModeService;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/recommendation-mode")
public class RecommendationModeController {
    private final RecommendationModeService modeService;

    // 홈 배너: 회원이면 V1(기본 설문) / V2(맞춤 질문) 중 무엇으로 갈지
    @GetMapping
    public ResponseEntity<ApiResponse<RecommendationModeResponse>> mode(@CurrentUserId Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ResponseEntity.ok(ApiResponse.success(modeService.mode(userId)));
    }
}
