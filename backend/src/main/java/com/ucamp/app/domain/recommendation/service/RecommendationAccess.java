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

/**
 * 추천 소유권 확인 (V5 보안). 추천의 주인이 아니면 RECOMMENDATION_NOT_FOUND(404)를 던진다.
 * 존재 여부도 숨겨 IDOR을 방지한다.
 */
@Component
@RequiredArgsConstructor
public class RecommendationAccess {
    private final AiRecommendationRepository recommendationRepository;
    private final SurveyQueryService surveyQueryService;

    @Transactional(readOnly = true)
    public AiRecommendation requireOwned(Long userId, Long recommendationId) {
        AiRecommendation rec = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECOMMENDATION_NOT_FOUND));
        if (!isOwner(userId, rec)) {
            throw new BusinessException(ErrorCode.RECOMMENDATION_NOT_FOUND);
        }
        return rec;
    }

    /** POST /recommendations: responseId의 주인이 요청자인지 확인 */
    @Transactional(readOnly = true)
    public void requireResponseOwned(Long userId, Long responseId) {
        SurveyResponse response = surveyQueryService.getResponse(responseId);
        if (userId == null || response.getUser() == null || !userId.equals(response.getUser().getId())) {
            throw new BusinessException(ErrorCode.RECOMMENDATION_NOT_FOUND);
        }
    }

    private boolean isOwner(Long userId, AiRecommendation rec) {
        if (userId == null) {
            return false;
        }
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
}
