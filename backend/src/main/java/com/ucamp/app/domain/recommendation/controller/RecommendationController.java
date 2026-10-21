package com.ucamp.app.domain.recommendation.controller;

import com.ucamp.app.domain.recommendation.dto.CreateRecommendationRequest;
import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;
import com.ucamp.app.domain.recommendation.service.RecommendationService;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
    private final RecommendationService recommendationService;

    // 동기 호출: LLM 응답까지 기다렸다가 결과를 돌려준다
    @PostMapping
    public ResponseEntity<ApiResponse<RecommendationResponse>> create(@RequestBody CreateRecommendationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(recommendationService.create(request.responseId())));
    }

    @GetMapping("/{recommendationId}")
    public ResponseEntity<ApiResponse<RecommendationResponse>> get(@PathVariable Long recommendationId) {
        return ResponseEntity.ok(ApiResponse.success(recommendationService.get(recommendationId)));
    }
}
