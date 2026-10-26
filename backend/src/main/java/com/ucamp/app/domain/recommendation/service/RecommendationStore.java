package com.ucamp.app.domain.recommendation.service;

import com.ucamp.app.domain.personalized.entity.AiQuestionSession;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// 추천 결과 저장·조회. LLM 호출 중에는 DB 트랜잭션을 잡지 않도록 저장만 따로 둔다
@Service
@RequiredArgsConstructor
public class RecommendationStore {
    private static final List<String> ACTIVE_OR_DONE =
            List.of(AiRecommendation.PENDING, AiRecommendation.PROCESSING, AiRecommendation.DONE);

    private final AiRecommendationRepository recommendationRepository;
    private final AiRecommendationItemRepository itemRepository;
    private final SurveyQueryService surveyQueryService;
    private final RecommendationMapper mapper;

    /** 동기 API: 결과를 바로 DONE으로 저장 */
    @Transactional
    public RecommendationResponse save(Long responseId, RecommendationResult result) {
        AiRecommendation rec = newRecommendation(responseId, AiRecommendation.DONE, result.summary(), result.model());
        return mapper.toResponse(rec, saveItems(rec, result));
    }

    /** V2 정밀 추천: 설문 응답 없이 맞춤 질문 회차로 DONE 저장 (response_id = null, session_id = 회차) */
    @Transactional
    public RecommendationResponse saveForSession(AiQuestionSession session, RecommendationResult result) {
        AiRecommendation rec = recommendationRepository.save(AiRecommendation.builder()
                .session(session)
                .summary(result.summary())
                .modelName(truncate(result.model(), AiRecommendation.MODEL_NAME_LENGTH))
                .status(AiRecommendation.DONE)
                .build());
        return mapper.toResponse(rec, saveItems(rec, result));
    }

    /** 비동기 API: 같은 응답의 진행 중·완료 작업 (멱등) */
    @Transactional(readOnly = true)
    public Optional<RecommendationResponse> findActiveOrDone(Long responseId) {
        return recommendationRepository
                .findFirstByResponse_ResponseIdAndStatusInOrderByRecommendationIdDesc(responseId, ACTIVE_OR_DONE)
                .map(this::toResponse);
    }

    /** 비동기 API: PENDING 행 생성 */
    @Transactional
    public RecommendationResponse createPending(Long responseId) {
        return mapper.toResponse(newRecommendation(responseId, AiRecommendation.PENDING, null, null), List.of());
    }

    /** 워커: PENDING → PROCESSING. 이미 다른 워커가 가져갔거나 끝난 작업이면 empty */
    @Transactional
    public Optional<Long> markProcessing(Long recommendationId) {
        return recommendationRepository.findById(recommendationId)
                // 큐에는 V1(설문 기반) 추천만 들어온다. V2 정밀 추천(response 없음)은 항상 동기로 만든다
                .filter(r -> AiRecommendation.PENDING.equals(r.getStatus()) && r.getResponse() != null)
                .map(r -> {
                    r.markProcessing();
                    return r.getResponse().getResponseId();
                });
    }

    /** 워커: 결과 저장 후 DONE */
    @Transactional
    public void complete(Long recommendationId, RecommendationResult result) {
        AiRecommendation rec = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECOMMENDATION_NOT_FOUND));
        saveItems(rec, result);
        rec.markDone(result.summary(), truncate(result.model(), AiRecommendation.MODEL_NAME_LENGTH));
    }

    @Transactional
    public void fail(Long recommendationId, String errorCode) {
        recommendationRepository.findById(recommendationId).ifPresent(r -> r.markFailed(truncate(errorCode, AiRecommendation.ERROR_CODE_LENGTH)));
    }

    @Transactional(readOnly = true)
    public List<Long> pendingIds() {
        return recommendationRepository.findByStatusOrderByRecommendationIdAsc(AiRecommendation.PENDING).stream()
                .map(AiRecommendation::getRecommendationId).toList();
    }

    @Transactional(readOnly = true)
    public Optional<RecommendationResponse> latestDoneForUser(Long userId) {
        return recommendationRepository.findLatestDoneByUserId(userId, PageRequest.of(0, 1)).stream()
                .findFirst().map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RecommendationResponse get(Long recommendationId) {
        return toResponse(recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECOMMENDATION_NOT_FOUND)));
    }

    private RecommendationResponse toResponse(AiRecommendation rec) {
        return mapper.toResponse(rec,
                itemRepository.findByRecommendation_RecommendationIdOrderBySortOrderAscRecommendationItemIdAsc(
                        rec.getRecommendationId()));
    }

    private AiRecommendation newRecommendation(Long responseId, String status, String summary, String model) {
        SurveyResponse response = surveyQueryService.getResponse(responseId);
        return recommendationRepository.save(AiRecommendation.builder()
                .response(response)
                .summary(summary)
                .modelName(truncate(model, AiRecommendation.MODEL_NAME_LENGTH))
                .status(status)
                .build());
    }

    private List<AiRecommendationItem> saveItems(AiRecommendation rec, RecommendationResult result) {
        List<AiRecommendationItem> items = new ArrayList<>();
        for (RecommendationItem item : result.itemsOrEmpty()) {
            items.add(AiRecommendationItem.builder()
                    .recommendation(rec)
                    .ingredientName(truncate(item.ingredient(), AiRecommendationItem.INGREDIENT_NAME_LENGTH))
                    .reason(item.reason())
                    .sortOrder(item.sortOrder())
                    .recommendedAmount(item.amount())
                    .currentIntake(item.currentIntake())
                    .intakeStatus(item.intakeStatus())
                    .unit(truncate(item.unit(), AiRecommendationItem.UNIT_LENGTH))
                    .doseNote(item.doseNote())
                    .excluded(item.excluded())
                    .appliedRuleIds(mapper.encodeRuleIds(item.appliedRuleIdsOrEmpty()))
                    .warnings(mapper.encodeWarnings(item.warningsOrEmpty()))
                    .build());
        }
        itemRepository.saveAll(items);
        return items;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
