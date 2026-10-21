package com.ucamp.app.domain.recommendation.controller;

import com.ucamp.app.domain.recommendation.dto.CreateRecommendationRequest;
import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.service.RecommendationService;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    // sync 모드: LLM 응답까지 기다렸다가 200. queue 모드: 접수 즉시 202 + recommendationId (GET으로 상태 조회)
    @PostMapping
    public ResponseEntity<ApiResponse<RecommendationResponse>> create(@RequestBody CreateRecommendationRequest request) {
        RecommendationResponse response = recommendationService.create(request.responseId());
        HttpStatus status = AiRecommendation.DONE.equals(response.status()) ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(ApiResponse.success(response));
    }

    @GetMapping("/{recommendationId}")
    public ResponseEntity<ApiResponse<RecommendationResponse>> get(@PathVariable Long recommendationId) {
        return ResponseEntity.ok(ApiResponse.success(recommendationService.get(recommendationId)));
    }
}
