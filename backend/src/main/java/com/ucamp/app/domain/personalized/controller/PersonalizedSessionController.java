package com.ucamp.app.domain.personalized.controller;

import com.ucamp.app.domain.personalized.dto.AnswerRequest;
import com.ucamp.app.domain.personalized.dto.PersonalizedSessionResponse;
import com.ucamp.app.domain.personalized.service.PersonalizedRecommendationService;
import com.ucamp.app.domain.personalized.service.PersonalizedSessionService;
import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// V2 회원 맞춤 질문 회차. 모든 API는 회원 전용 (X-User-Id 없으면 401). TODO: 로그인 연동 시 @CurrentUserId만 교체
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/personalized-sessions")
public class PersonalizedSessionController {
    private final PersonalizedSessionService sessionService;
    private final PersonalizedRecommendationService recommendationService;

    // 회차 시작: 임신 고정 질문 또는 첫 LLM 질문
    @PostMapping
    public ResponseEntity<ApiResponse<PersonalizedSessionResponse>> start(@CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.success(sessionService.start(member(userId))));
    }

    // 답변 → 다음 질문 또는 READY
    @PostMapping("/{sessionId}/answers")
    public ResponseEntity<ApiResponse<PersonalizedSessionResponse>> answer(@CurrentUserId Long userId,
                                                                           @PathVariable Long sessionId,
                                                                           @RequestBody AnswerRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                sessionService.answer(member(userId), sessionId, request.turnId(), request.selected())));
    }

    // 그만하기: 지금까지 답으로 정밀 추천을 받을 수 있게 READY
    @PostMapping("/{sessionId}/stop")
    public ResponseEntity<ApiResponse<PersonalizedSessionResponse>> stop(@CurrentUserId Long userId,
                                                                         @PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success(sessionService.stop(member(userId), sessionId)));
    }

    // 정밀 추천: READY일 때만. 응답은 V1 추천과 같은 형식. 같은 회차로 다시 부르면 저장된 결과
    @PostMapping("/{sessionId}/recommendation")
    public ResponseEntity<ApiResponse<RecommendationResponse>> recommend(@CurrentUserId Long userId,
                                                                        @PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success(recommendationService.recommend(member(userId), sessionId)));
    }

    static Long member(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
