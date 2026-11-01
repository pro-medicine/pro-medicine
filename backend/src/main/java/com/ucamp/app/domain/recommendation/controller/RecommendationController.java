package com.ucamp.app.domain.recommendation.controller;

import com.ucamp.app.domain.recommendation.dto.CreateRecommendationRequest;
import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;
import com.ucamp.app.domain.recommendation.service.RecommendationAccess;
import com.ucamp.app.domain.recommendation.service.RecommendationService;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
    private final RecommendationService recommendationService;
    private final RecommendationAccess recommendationAccess;

    // sync 모드: LLM 응답까지 기다렸다가 200. queue 모드: 접수 즉시 202 + recommendationId (GET으로 상태 조회)
    @PostMapping
    public ResponseEntity<ApiResponse<RecommendationResponse>> create(
            @CurrentUserId Long userId,
            @RequestBody CreateRecommendationRequest request,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken,
            HttpServletRequest httpRequest) {
        RecommendationResponse response = recommendationService.create(userId, request.responseId(),
                guestToken, httpRequest);
        HttpStatus status = response.isDone() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(ApiResponse.success(response));
    }

    @GetMapping("/{recommendationId}")
    public ResponseEntity<ApiResponse<RecommendationResponse>> get(
            @CurrentUserId Long userId,
            @PathVariable Long recommendationId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        recommendationAccess.requireOwned(userId, recommendationId, guestToken);
        return ResponseEntity.ok(ApiResponse.success(recommendationService.get(recommendationId)));
    }
}
