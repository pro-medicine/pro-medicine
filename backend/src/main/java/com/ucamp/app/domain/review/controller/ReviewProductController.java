package com.ucamp.app.domain.review.controller;

import com.ucamp.app.domain.review.dto.ReviewProductResponse;
import com.ucamp.app.domain.review.service.ReviewProductRecommendationService;
import com.ucamp.app.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// 성분 카드 → 약사 리뷰 기반 제품 추천 (V3). 권한은 GET /api/v1/recommendations/{id}와 같다
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recommendations/{recommendationId}/products")
public class ReviewProductController {
    private final ReviewProductRecommendationService service;

    @GetMapping
    public ResponseEntity<ApiResponse<ReviewProductResponse>> products(@PathVariable Long recommendationId,
                                                                       @RequestParam String ingredient) {
        return ResponseEntity.ok(ApiResponse.success(service.recommend(recommendationId, ingredient)));
    }

    // 성분 코드 → 안전 필터를 통과한 제품이 1개 이상인지. 화면은 true일 때만 [제품 추천 보기] 버튼을 띄운다
    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> availability(@PathVariable Long recommendationId) {
        return ResponseEntity.ok(ApiResponse.success(service.availability(recommendationId)));
    }
}
