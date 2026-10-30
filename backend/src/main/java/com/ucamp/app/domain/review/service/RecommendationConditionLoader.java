package com.ucamp.app.domain.review.service;

import com.ucamp.app.domain.personalized.entity.AiQuestionSession;
import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationRepository;
import com.ucamp.app.domain.recommendation.rule.ConditionCodeResolver;
import com.ucamp.app.domain.recommendation.service.RecommendationInputAssembler;
import com.ucamp.app.domain.recommendation.service.RecommendationStore;
import com.ucamp.app.domain.review.safety.ProductSafetyFilter;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 추천 결과 → 제품 안전 필터에 쓸 사용자 조건. 기존 추천과 같은 입력 조립·조건 코드를 그대로 쓴다.
 * - 설문 기반(V1): assemble(responseId) / 맞춤 질문 기반(V2): assembleForMember(회차 회원, 회차 임신 답)
 * - 조건은 지금 프로필 기준 (추천 후 약·질환을 바꾸면 바뀐 값으로 판단. 설계 11 P6)
 * - 회차가 90일 뒤 삭제돼 입력을 만들 수 없으면 추천 성분을 비워 제품을 내지 않는다 (안전 확인 불가)
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationConditionLoader {
    private final AiRecommendationRepository recommendationRepository;
    private final RecommendationStore recommendationStore;
    private final RecommendationInputAssembler assembler;
    private final ConditionCodeResolver conditionCodeResolver;

    /** 추천 성분(제외 안 된 것, 추천 순서)과 사용자 조건. 입력을 만들 수 없으면 성분 없음 + user null */
    public record Loaded(List<String> recommendedIngredients, ProductSafetyFilter.UserConditions user) {
        static Loaded empty() {
            return new Loaded(List.of(), null);
        }
    }

    public Loaded load(Long recommendationId) {
        AiRecommendation rec = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECOMMENDATION_NOT_FOUND));
        // PENDING·FAILED는 items가 비어 있다
        List<String> recommended = recommendationStore.get(recommendationId).items().stream()
                .map(RecommendationResponse.Item::ingredient).toList();
        RecommendationInput input = input(rec);
        if (input == null) {
            return Loaded.empty();
        }
        Set<String> conditions = conditionCodeResolver.resolve(input);
        boolean hasMedicationOrDisease = !input.medicationsOrEmpty().isEmpty() || !input.conditionsOrEmpty().isEmpty();
        return new Loaded(recommended, new ProductSafetyFilter.UserConditions(conditions, input.isGuest(),
                hasMedicationOrDisease, recommended));
    }

    private RecommendationInput input(AiRecommendation rec) {
        if (rec.getResponse() != null) {
            return assembler.assemble(rec.getResponse().getResponseId());
        }
        AiQuestionSession session = rec.getSession();
        if (session != null) {
            return assembler.assembleForMember(session.getUser().getId(), session.getPregnancy());
        }
        return null;
    }
}
