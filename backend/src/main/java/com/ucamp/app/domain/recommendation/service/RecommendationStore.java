package com.ucamp.app.domain.recommendation.service;

import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.entity.AiRecommendationItem;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationItem;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationResult;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationItemRepository;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationRepository;
import com.ucamp.app.domain.survey.entity.SurveyResponse;
import com.ucamp.app.domain.survey.service.SurveyQueryService;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// 추천 결과 저장·조회. LLM 호출 중에는 DB 트랜잭션을 잡지 않도록 저장만 따로 둔다
@Service
@RequiredArgsConstructor
public class RecommendationStore {
    private static final int MAX_NAME_LENGTH = 150;
    private static final int MAX_MODEL_LENGTH = 60;
    private static final int MAX_UNIT_LENGTH = 10;

    private final AiRecommendationRepository recommendationRepository;
    private final AiRecommendationItemRepository itemRepository;
    private final SurveyQueryService surveyQueryService;

    @Transactional
    public RecommendationResponse save(Long responseId, RecommendationResult result) {
        SurveyResponse response = surveyQueryService.getResponse(responseId);
        String model = result.model() == null ? null
                : result.model().length() > MAX_MODEL_LENGTH ? result.model().substring(0, MAX_MODEL_LENGTH) : result.model();
        AiRecommendation rec = recommendationRepository.save(AiRecommendation.builder()
                .response(response)
                .summary(result.summary())
                .modelName(model)
                .build());
        List<AiRecommendationItem> items = new ArrayList<>();
        for (RecommendationItem item : result.itemsOrEmpty()) {
            String name = item.ingredient().length() > MAX_NAME_LENGTH
                    ? item.ingredient().substring(0, MAX_NAME_LENGTH) : item.ingredient();
            items.add(AiRecommendationItem.builder()
                    .recommendation(rec)
                    .ingredientName(name)
                    .reason(item.reason())
                    .sortOrder(item.sortOrder())
                    .recommendedAmount(item.amount())
                    .currentIntake(item.currentIntake())
                    .intakeStatus(item.intakeStatus())
                    .unit(item.unit() == null || item.unit().length() <= MAX_UNIT_LENGTH ? item.unit()
                            : item.unit().substring(0, MAX_UNIT_LENGTH))
                    .doseNote(item.doseNote())
                    .build());
        }
        itemRepository.saveAll(items);
        return RecommendationResponse.of(rec, items);
    }

    @Transactional(readOnly = true)
    public RecommendationResponse get(Long recommendationId) {
        AiRecommendation rec = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECOMMENDATION_NOT_FOUND));
        return RecommendationResponse.of(rec,
                itemRepository.findByRecommendation_RecommendationIdOrderBySortOrderAscRecommendationItemIdAsc(recommendationId));
    }
}
