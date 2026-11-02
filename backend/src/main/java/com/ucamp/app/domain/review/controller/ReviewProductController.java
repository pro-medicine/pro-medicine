package com.ucamp.app.domain.review.controller;

import com.ucamp.app.domain.recommendation.service.RecommendationAccess;
import com.ucamp.app.domain.review.dto.ReviewProductResponse;
import com.ucamp.app.domain.review.service.ReviewProductRecommendationService;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// 성분 카드 → 약사 리뷰 기반 제품 추천 (V3). 회원 전용 — 비회원은 401
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/recommendations/{recommendationId}/products")
public class ReviewProductController {
    private final ReviewProductRecommendationService service;
    private final RecommendationAccess recommendationAccess;

    // ingredient가 없거나 성분 코드가 아니면 400 (GlobalExceptionHandler가 누락 파라미터를 500으로 내므로 서비스에서 검증)
    @GetMapping
    public ResponseEntity<ApiResponse<ReviewProductResponse>> products(
            @CurrentUserId Long userId,
            @PathVariable Long recommendationId,
            @RequestParam(required = false) String ingredient,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        requireMember(userId);
        recommendationAccess.requireOwned(userId, recommendationId, guestToken);
        return ResponseEntity.ok(ApiResponse.success(service.recommend(recommendationId, ingredient)));
    }

    // 성분 코드 → 안전 필터를 통과한 제품이 1개 이상인지. 화면은 true일 때만 [제품 추천 보기] 버튼을 띄운다
    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> availability(
            @CurrentUserId Long userId,
            @PathVariable Long recommendationId,
            @RequestHeader(value = "X-Guest-Token", required = false) String guestToken) {
        requireMember(userId);
        recommendationAccess.requireOwned(userId, recommendationId, guestToken);
        return ResponseEntity.ok(ApiResponse.success(service.availability(recommendationId)));
    }

    // 비회원은 제품 추천을 제공하지 않는다. requireOwned보다 먼저 검사해 추천 존재 여부가 새지 않도록 한다.
    private static void requireMember(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
