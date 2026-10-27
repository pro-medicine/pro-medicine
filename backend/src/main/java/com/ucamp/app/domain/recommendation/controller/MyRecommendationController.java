package com.ucamp.app.domain.recommendation.controller;

import com.ucamp.app.domain.recommendation.dto.LatestRecommendationResponse;
import com.ucamp.app.domain.recommendation.service.RecommendationReuseService;
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
@RequestMapping("/api/v1/users/me/recommendations")
public class MyRecommendationController {
    private final RecommendationReuseService reuseService;

    // 알림을 열고 들어온 회원: 저장된 최근 추천을 재사용할 수 있으면 LLM 없이 돌려준다
    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<LatestRecommendationResponse>> latest(@CurrentUserId Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ResponseEntity.ok(ApiResponse.success(reuseService.latest(userId)));
    }
}
