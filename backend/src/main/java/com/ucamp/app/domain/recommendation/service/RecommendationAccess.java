package com.ucamp.app.domain.recommendation.service;

import com.ucamp.app.domain.chat.entity.AiChatSession;
import com.ucamp.app.domain.personalized.entity.AiQuestionSession;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationRepository;
import com.ucamp.app.domain.survey.entity.SurveyResponse;
import com.ucamp.app.domain.survey.service.SurveyQueryService;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 추천 소유권 확인 (V5 보안). 추천의 주인이 아니면 RECOMMENDATION_NOT_FOUND(404)를 던진다.
 * 존재 여부도 숨겨 IDOR을 방지한다.
 * 비회원은 X-Guest-Token 헤더의 SHA-256 해시를 DB 저장 해시와 constant-time 비교한다.
 */
@Component
@RequiredArgsConstructor
public class RecommendationAccess {
    private final AiRecommendationRepository recommendationRepository;
    private final SurveyQueryService surveyQueryService;

    @Transactional(readOnly = true)
    public AiRecommendation requireOwned(Long userId, Long recommendationId, String guestToken) {
        AiRecommendation rec = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECOMMENDATION_NOT_FOUND));
        if (!isOwner(userId, rec, guestToken)) {
            throw new BusinessException(ErrorCode.RECOMMENDATION_NOT_FOUND);
        }
        return rec;
    }

    /** POST /recommendations: responseId의 주인이 요청자인지 확인 */
    @Transactional(readOnly = true)
    public void requireResponseOwned(Long userId, Long responseId, String guestToken) {
        SurveyResponse response = surveyQueryService.getResponse(responseId);
        if (userId == null) {
            // 비회원 응답: 토큰 해시 비교
            if (response.getUser() != null || !verifyGuestToken(guestToken, response.getGuestTokenHash())) {
                throw new BusinessException(ErrorCode.RECOMMENDATION_NOT_FOUND);
            }
            return;
        }
        // 회원은 JWT로 인증되므로 stale guestToken은 무시. 회원 rec가 아닌 경우만 거부
        if (response.getUser() == null || !userId.equals(response.getUser().getId())) {
            throw new BusinessException(ErrorCode.RECOMMENDATION_NOT_FOUND);
        }
    }

    private boolean isOwner(Long userId, AiRecommendation rec, String guestToken) {
        if (userId == null) {
            // 비회원: 설문 응답의 게스트 토큰 해시와 비교
            SurveyResponse response = rec.getResponse();
            return response != null && response.getUser() == null
                    && verifyGuestToken(guestToken, response.getGuestTokenHash());
        }
        // 회원은 JWT로 인증되므로 stale guestToken이 있어도 무시하고 소유권만 확인
        // V1: 설문 응답 → 사용자
        SurveyResponse response = rec.getResponse();
        if (response != null) {
            return response.getUser() != null && userId.equals(response.getUser().getId());
        }
        // V2: 맞춤 질문 회차 → 사용자
        AiQuestionSession session = rec.getSession();
        if (session != null) {
            return userId.equals(session.getUser().getId());
        }
        // V4 track 2: 대화 세션 → 사용자
        AiChatSession chatSession = rec.getChatSession();
        if (chatSession != null) {
            return userId.equals(chatSession.getUser().getId());
        }
        // 90일 삭제로 FK가 모두 null이면 소유자 확인 불가 → 거부
        return false;
    }

    /** 게스트 토큰 검증: constant-time 비교 (타이밍 공격 방지) */
    private static boolean verifyGuestToken(String guestToken, String storedHash) {
        if (guestToken == null || guestToken.isEmpty() || storedHash == null) {
            return false;
        }
        byte[] computed = sha256Bytes(guestToken);
        byte[] stored = HexFormat.of().parseHex(storedHash);
        return MessageDigest.isEqual(computed, stored);
    }

    private static byte[] sha256Bytes(String input) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
