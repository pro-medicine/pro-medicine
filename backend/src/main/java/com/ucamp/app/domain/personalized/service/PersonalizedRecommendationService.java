package com.ucamp.app.domain.personalized.service;

import com.ucamp.app.domain.personalized.entity.AiQuestionSession;
import com.ucamp.app.domain.personalized.planner.Topic;
import com.ucamp.app.domain.personalized.planner.TopicPlanner;
import com.ucamp.app.domain.personalized.question.TopicPurposes;
import com.ucamp.app.domain.recommendation.config.RecommendationProperties;
import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationResult;
import com.ucamp.app.domain.recommendation.pipeline.RuleOnlyPipeline;
import com.ucamp.app.domain.recommendation.service.RecommendationInputAssembler;
import com.ucamp.app.domain.recommendation.service.RecommendationService;
import com.ucamp.app.domain.recommendation.service.RecommendationStore;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * V2 정밀 추천 (설계 7장). 프로필 + 회차 임신 답 + 맞춤 답변으로 입력을 만들고 기존 추천 파이프라인을 그대로 쓴다.
 * 조건 코드·사전 금지 목록·사후 규칙·서버 용량 계산은 프로필(과 임신 답) 기준으로 그대로 적용되고,
 * 맞춤 답변은 LLM의 성분 선택과 이유에만 영향을 준다. 결과는 V1과 같은 형식으로 ai_recommendations에 저장한다.
 * 항상 동기 처리한다 (큐 워커는 설문 응답 기준 입력만 다룬다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalizedRecommendationService {
    /** 규칙(사전 금지·사후 검증)이 적용되는 버전. 설정이 이보다 낮으면(V0~V3) V4를 쓴다 */
    public static final Set<String> RULE_VERSIONS = Set.of("V4", "V6", "V7", "V8", RuleOnlyPipeline.VERSION);
    public static final String DEFAULT_VERSION = "V4";

    private final PersonalizedSessionStore sessionStore;
    private final PersonalizedSessionService sessionService;
    private final RecommendationInputAssembler assembler;
    private final RecommendationService recommendationService;
    private final RecommendationStore recommendationStore;
    private final RecommendationProperties properties;
    private final MeterRegistry meterRegistry;

    public RecommendationResponse recommend(Long userId, Long sessionId) {
        SessionState state = sessionStore.get(userId, sessionId);
        if (state.is(AiQuestionSession.DONE) && state.recommendationId() != null) {
            return recommendationStore.get(state.recommendationId());
        }
        if (!state.is(AiQuestionSession.READY)) {
            throw new BusinessException(ErrorCode.PERSONALIZED_INVALID_STATE);
        }
        RecommendationInput input = input(userId, state);
        RecommendationResult result = recommendationService.runInput(input, pipelineVersion());
        PersonalizedSessionStore.Completed completed = sessionStore.complete(userId, sessionId, result);
        if (!completed.alreadyDone()) {
            sessionService.countSession(AiQuestionSession.DONE, completed.stoppedByUser());
            DistributionSummary.builder("pq.session.questions").register(meterRegistry)
                    .record(completed.questionCount());
            RecommendationDiff diff = completed.diff();
            if (diff != null) {
                meterRegistry.counter("pq.recommendation.diff", "type", "added").increment(diff.added().size());
                meterRegistry.counter("pq.recommendation.diff", "type", "removed").increment(diff.removed().size());
                meterRegistry.counter("pq.recommendation.diff", "type", "rank").increment(diff.rankChanged().size());
            }
        }
        return completed.response();
    }

    /** 정밀 추천 입력: 프로필 + 임신 답 + 맞춤 답변 (평가 러너도 같은 입력을 쓴다) */
    public RecommendationInput input(Long userId, SessionState state) {
        RecommendationInput profile = assembler.assembleForMember(userId, state.pregnancy());
        Map<String, Topic> topics = new TopicPlanner()
                .plan(sessionService.plannerProfile(profile), state.plannerHistory()).stream()
                .collect(Collectors.toMap(Topic::code, Function.identity(), (a, b) -> a));
        List<RecommendationInput.PersonalizedAnswer> answers = state.answeredLlmTurns().stream()
                .map(t -> new RecommendationInput.PersonalizedAnswer(t.topic(),
                        topics.containsKey(t.topic()) ? TopicPurposes.label(topics.get(t.topic())) : t.topic(),
                        t.question(), t.selectedOptions().stream().map(TurnOption::label).toList()))
                .toList();
        return profile.toBuilder().personalizedAnswers(answers).sessionId(state.sessionId()).build();
    }

    public String pipelineVersion() {
        String configured = properties.getPipelineVersion();
        return RULE_VERSIONS.contains(configured) ? configured : DEFAULT_VERSION;
    }
}
