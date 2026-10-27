package com.ucamp.app.domain.personalized.service;

import com.ucamp.app.domain.personalized.config.PersonalizedProperties;
import com.ucamp.app.domain.personalized.dto.PersonalizedSessionResponse;
import com.ucamp.app.domain.personalized.dto.PersonalizedSessionResponse.OptionView;
import com.ucamp.app.domain.personalized.dto.PersonalizedSessionResponse.Progress;
import com.ucamp.app.domain.personalized.dto.PersonalizedSessionResponse.QuestionView;
import com.ucamp.app.domain.personalized.dto.RecommendationModeResponse;
import com.ucamp.app.domain.personalized.entity.AiQuestionSession;
import com.ucamp.app.domain.personalized.planner.Topic;
import com.ucamp.app.domain.personalized.planner.TopicPlanner;
import com.ucamp.app.domain.personalized.question.GeneratedQuestion;
import com.ucamp.app.domain.personalized.question.PersonalizedQuestionGenerator;
import com.ucamp.app.domain.personalized.question.QuestionBatchGenerator;
import com.ucamp.app.domain.personalized.question.QuestionGenerationException;
import com.ucamp.app.domain.personalized.prefetch.PrefetchStrategy;
import com.ucamp.app.domain.personalized.prefetch.QuestionPrefetcher;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.rule.Rule;
import com.ucamp.app.domain.recommendation.rule.RuleService;
import com.ucamp.app.domain.recommendation.service.RecommendationInputAssembler;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import com.ucamp.app.infra.llm.LlmRequest;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * V2 맞춤 질문 회차 (설계 4-2 ~ 4-4). 순서: 답 저장(트랜잭션) → 다음 주제 결정(서버) → 질문 생성(LLM, 트랜잭션 없음) → 질문 저장.
 * 주제·깊이·종료는 TopicPlanner가 정하고 LLM은 질문 문장과 선택지만 만든다.
 * 맞춤 답변은 프로필(health_profiles 등)에 저장하지 않는다 (D9).
 * 질문 미리 만들기(pq.prefetch-strategy)가 켜져 있으면 미리 만든 질문을 먼저 쓰고, 없거나 쓸 수 없으면 단건 생성한다.
 */
@Service
@RequiredArgsConstructor
public class PersonalizedSessionService {
    public static final String DRUG_CONDITION = "DRUG";

    private final RecommendationModeService modeService;
    private final RecommendationInputAssembler assembler;
    private final RuleService ruleService;
    private final PersonalizedSessionStore store;
    private final PersonalizedQuestionGenerator generator;
    private final QuestionPrefetcher prefetcher;
    private final PersonalizedProperties properties;
    private final MeterRegistry meterRegistry;
    private final TopicPlanner planner = new TopicPlanner();

    /** 회차 시작. 진행 중인 회차는 ABANDONED. 19~50세 여성이면 임신 고정 질문, 아니면 첫 LLM 질문(불편한 점) */
    public PersonalizedSessionResponse start(Long userId) {
        RecommendationModeResponse mode = modeService.mode(userId);
        if (!mode.isV2()) {
            throw new ProfileIncompleteException(mode.missing());
        }
        RecommendationInput profile = assembler.assembleForMember(userId, null);
        PersonalizedSessionStore.Started started = store.start(userId, PregnancyQuestion.applies(profile));
        for (Long abandoned : started.abandonedIds()) {
            prefetcher.finish(abandoned);
            countSession(AiQuestionSession.ABANDONED, false);
        }
        SessionState state = started.state();
        prefetchAtStart(state, profile);
        if (state.pendingTurn() != null) {
            return response(state);
        }
        return askNext(state, profile);
    }

    public PersonalizedSessionResponse answer(Long userId, Long sessionId, Long turnId, List<Integer> selected) {
        if (turnId == null) {
            throw new BusinessException(ErrorCode.PERSONALIZED_INVALID_ANSWER);
        }
        SessionState state = store.answer(userId, sessionId, turnId, selected);
        SessionState.TurnState answered = state.turns().stream().filter(t -> t.turnId().equals(turnId))
                .findFirst().orElseThrow();
        if (!answered.fixed()) {
            answered.selectedOptions().forEach(o ->
                    meterRegistry.counter("pq.answer.kind", "kind", o.kind().name()).increment());
        }
        RecommendationInput profile = assembler.assembleForMember(userId, state.pregnancy());
        if (!answered.fixed() && Topic.CONCERN_OPEN.code().equals(answered.topic())) {
            prefetchConcerns(state, profile);
        }
        return askNext(state, profile);
    }

    public PersonalizedSessionResponse stop(Long userId, Long sessionId) {
        SessionState state = store.stop(userId, sessionId);
        prefetcher.finish(sessionId);
        return response(state);
    }

    /** ✕ 닫기: 추천 없이 ABANDONED (사용자 중단) */
    public PersonalizedSessionResponse close(Long userId, Long sessionId) {
        SessionState before = store.get(userId, sessionId);
        SessionState state = store.close(userId, sessionId);
        prefetcher.finish(sessionId);
        if (!before.is(AiQuestionSession.ABANDONED)) {
            countSession(AiQuestionSession.ABANDONED, true);
        }
        return response(state);
    }

    /** 회차 상태 그대로 (정밀 추천 등 다른 서비스용) */
    public SessionState state(Long userId, Long sessionId) {
        return store.get(userId, sessionId);
    }

    // 다음 주제를 정하고 질문을 만든다. 남은 주제가 없으면 READY
    private PersonalizedSessionResponse askNext(SessionState state, RecommendationInput profile) {
        Optional<TopicPlanner.Next> next = planner.next(plannerProfile(profile), state.plannerHistory());
        if (next.isEmpty()) {
            SessionState ready = store.markReady(state.sessionId());
            prefetcher.finish(state.sessionId());
            return response(ready);
        }
        GeneratedQuestion q;
        try {
            q = prefetcher.take(state.sessionId(), next.get().topic(), next.get().depth(), askedQuestions(state))
                    .orElseGet(() -> generator.generate(new PersonalizedQuestionGenerator.Context(state.sessionId(),
                            profile, next.get().topic(), next.get().depth(), pastQuestions(state))));
        } catch (QuestionGenerationException e) {
            store.markFailed(state.sessionId(), ErrorCode.PERSONALIZED_QUESTION_FAILED.getCode());
            prefetcher.finish(state.sessionId());
            countSession(AiQuestionSession.FAILED, false);
            throw new BusinessException(ErrorCode.PERSONALIZED_QUESTION_FAILED);
        }
        // 생성 중에 그만하기가 오면 만든 질문은 버리고 지금 상태를 돌려준다
        return response(store.addTurn(state.sessionId(), next.get().topic(), next.get().depth(), q)
                .orElseGet(() -> store.get(state.userId(), state.sessionId())));
    }

    public TopicPlanner.Profile plannerProfile(RecommendationInput in) {
        Set<String> ruledClasses = ruleService.engine().rules().stream()
                .filter(r -> DRUG_CONDITION.equals(r.conditionType()))
                .map(Rule::conditionCode)
                .collect(Collectors.toSet());
        return new TopicPlanner.Profile(
                in.medicationsOrEmpty().stream().map(m -> new TopicPlanner.Medication(m.drugClass(), m.name())).toList(),
                ruledClasses, in.conditionsOrEmpty(), in.smoking(), in.drinking(), in.caffeine(), in.dietType(),
                in.sunExposure());
    }

    // ---------- 질문 미리 만들기 (pq.prefetch-strategy) ----------

    /**
     * 회차 시작 때 묶음 생성. 묶음 주제 = 계획 순서의 프로필 주제(약 → 질환 → 생활습관) 1단계, 최대 pq.prefetch-topics개.
     * ① BACKGROUND: 첫 질문은 지금처럼 만들고, 묶음은 백그라운드
     * ②③ UPFRONT: 불편한 점까지 한 번에. 첫 질문이 임신 고정 질문이면 기다릴 필요가 없어 백그라운드, 아니면 지금 바로
     */
    private void prefetchAtStart(SessionState state, RecommendationInput profile) {
        PrefetchStrategy strategy = prefetcher.strategy();
        if (strategy == PrefetchStrategy.NONE) {
            return;
        }
        List<QuestionBatchGenerator.Item> items = new ArrayList<>();
        if (strategy.upfront()) {
            items.add(new QuestionBatchGenerator.Item(Topic.CONCERN_OPEN, false));
        }
        planner.plan(plannerProfile(profile), List.of()).stream()
                .filter(t -> t.type() != Topic.Type.CONCERN_OPEN && t.type() != Topic.Type.CONCERN)
                .limit(properties.getPrefetchTopics())
                .forEach(t -> items.add(new QuestionBatchGenerator.Item(t, strategy.followup() && t.maxDepth() > 1)));
        boolean async = !strategy.upfront() || state.pendingTurn() != null;
        prefetcher.submit(new QuestionBatchGenerator.Request(state.sessionId(), profile, items, List.of(),
                LlmRequest.TAG_PQ_QUESTION_BATCH), async);
    }

    /** ③ 불편한 점에 답하면 파생 주제(최대 2개)의 1단계 + CONCERN일 때의 2단계를 백그라운드로 한 번에 */
    private void prefetchConcerns(SessionState state, RecommendationInput profile) {
        if (!prefetcher.strategy().followup()) {
            return;
        }
        List<QuestionBatchGenerator.Item> items = planner.plan(plannerProfile(profile), state.plannerHistory()).stream()
                .filter(t -> t.type() == Topic.Type.CONCERN)
                .map(t -> new QuestionBatchGenerator.Item(t, t.maxDepth() > 1))
                .toList();
        if (!items.isEmpty()) {
            prefetcher.submit(new QuestionBatchGenerator.Request(state.sessionId(), profile, items, pastQuestions(state),
                    LlmRequest.TAG_PQ_QUESTION_FOLLOWUP_BATCH), true);
        }
    }

    // 이 회차에서 이미 낸 LLM 질문 문장 (미리 만든 질문의 반복 검사용)
    private static List<String> askedQuestions(SessionState state) {
        return state.turns().stream().filter(t -> !t.fixed()).map(SessionState.TurnState::question).toList();
    }

    private static List<PersonalizedQuestionGenerator.PastQuestion> pastQuestions(SessionState state) {
        return state.answeredLlmTurns().stream()
                .map(t -> new PersonalizedQuestionGenerator.PastQuestion(t.question(),
                        t.selectedOptions().stream().map(TurnOption::label).toList()))
                .toList();
    }

    public static PersonalizedSessionResponse response(SessionState state) {
        SessionState.TurnState pending = state.is(AiQuestionSession.IN_PROGRESS) ? state.pendingTurn() : null;
        return new PersonalizedSessionResponse(state.sessionId(), state.status(), pending == null ? null : view(pending),
                new Progress(state.questionCount(), TopicPlanner.BASE_QUESTIONS, TopicPlanner.MAX_QUESTIONS),
                state.is(AiQuestionSession.IN_PROGRESS) && PersonalizedSessionStore.canStop(state));
    }

    private static QuestionView view(SessionState.TurnState t) {
        List<OptionView> options = IntStream.range(0, t.options().size())
                .mapToObj(i -> new OptionView(i, t.options().get(i).label(), t.options().get(i).kind().isExclusive()))
                .toList();
        return new QuestionView(t.turnId(), t.turnNo(), t.fixed(), t.topic(), t.question(), t.answerType(),
                Topic.MULTI.equals(t.answerType()) ? TopicPlanner.MAX_CONCERN_TOPICS : 1, options);
    }

    void countSession(String status, boolean stopped) {
        meterRegistry.counter("pq.session", "status", status, "stopped", String.valueOf(stopped)).increment();
    }
}
