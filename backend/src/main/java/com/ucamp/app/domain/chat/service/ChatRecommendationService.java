package com.ucamp.app.domain.chat.service;

import com.ucamp.app.domain.chat.config.ChatProperties;
import com.ucamp.app.domain.chat.dto.ChatCard;
import com.ucamp.app.domain.chat.dto.ChatMessageRequest;
import com.ucamp.app.domain.chat.dto.ChatMessageResponse;
import com.ucamp.app.domain.chat.dto.ChatSessionResponse;
import com.ucamp.app.domain.chat.dto.ChatStartResponse;
import com.ucamp.app.domain.chat.entity.AiChatResult;
import com.ucamp.app.domain.chat.entity.AiChatSession;
import com.ucamp.app.domain.chat.parse.ChatParse;
import com.ucamp.app.domain.chat.parse.ChatParseException;
import com.ucamp.app.domain.chat.parse.ChatParser;
import com.ucamp.app.domain.chat.parse.RedFlagDetector;
import com.ucamp.app.domain.chat.scoring.ReviewTrack;
import com.ucamp.app.domain.chat.vector.ReviewVectorIndex;
import com.ucamp.app.domain.personalized.service.PregnancyQuestion;
import com.ucamp.app.domain.personalized.service.RecommendationModeService;
import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.service.RecommendationInputAssembler;
import com.ucamp.app.domain.review.dto.ReviewProductResponse;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import com.ucamp.app.infra.llm.LlmCallException;
import com.ucamp.app.infra.llm.embed.EmbeddingClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * V4 대화창 (설계 3). 입력 → ① LLM 구조화 → 응급·주제 밖·되묻기 → ② 안전 입력 → ③ 트랙 1 → 결과 없으면 ④ 트랙 2.
 * 추천 결정은 서버 코드가 한다 (D3): LLM은 문장을 JSON으로 바꾸기만 하고, 안전 판단은 RuleEngine·ProductSafetyFilter.
 * 대화 원문은 ai_chat_turns에만 저장하고 애플리케이션 로그에 남기지 않는다 (건강 정보)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRecommendationService {
    public static final String REQUEST_METRIC = "chat.request";
    public static final String RESULT_METRIC = "chat.result";
    public static final String CHAT_TOPIC = "CHAT";
    static final String CHAT_QUESTION = "사용자가 대화창에 쓴 고민";

    private final ChatSessionStore sessionStore;
    private final ChatParser parser;
    private final ChatInputAssembler chatInputAssembler;
    private final RecommendationInputAssembler recommendationInputAssembler;
    private final RecommendationModeService modeService;
    private final EmbeddingClient embeddingClient;
    private final ReviewVectorIndex vectorIndex;
    private final ReviewTrack reviewTrack;
    private final ChatFallbackRunner fallbackRunner;
    private final ChatProperties properties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    // 같은 세션에 메시지가 동시에 와도 하나씩 처리한다 (LLM 호출 중에는 DB 잠금을 잡지 않는다)
    private final Map<Long, Object> sessionLocks = new ConcurrentHashMap<>();

    /** 대화 시작. 건강정보가 없는 회원은 V2와 같은 기준으로 막는다 (설계 §11 P1) */
    public ChatStartResponse start(Long userId) {
        if (!modeService.mode(userId).isV2()) {
            throw new BusinessException(ErrorCode.CHAT_PROFILE_INCOMPLETE);
        }
        RecommendationInput profile = recommendationInputAssembler.assembleForMember(userId, null);
        Long id = sessionStore.start(userId);
        ChatStartResponse.PregnancyQuestion question = PregnancyQuestion.applies(profile)
                ? new ChatStartResponse.PregnancyQuestion(PregnancyQuestion.TEXT, PregnancyQuestion.OPTIONS.stream()
                .map(o -> new ChatStartResponse.Option(o.label(), o.code())).toList())
                : null;
        return new ChatStartResponse(id, question);
    }

    public ChatMessageResponse message(Long userId, Long chatSessionId, ChatMessageRequest request) {
        String text = request == null || request.text() == null ? "" : request.text().strip();
        if (text.isEmpty() || text.length() > properties.getInput().getMaxLength()) {
            throw new BusinessException(ErrorCode.CHAT_INVALID_INPUT);
        }
        Object lock = sessionLocks.computeIfAbsent(chatSessionId, k -> new Object());
        synchronized (lock) {
            try {
                return timed("total", () -> handle(userId, chatSessionId, text, request.pregnancy()));
            } finally {
                sessionLocks.remove(chatSessionId, lock);
            }
        }
    }

    /** 결과 다시 보기 (스냅샷 그대로) */
    public ChatSessionResponse get(Long userId, Long chatSessionId) {
        ChatSessionStore.State state = sessionStore.state(userId, chatSessionId);
        ChatMessageResponse result = sessionStore.result(chatSessionId).map(r -> snapshot(chatSessionId, r))
                .orElse(null);
        return new ChatSessionResponse(chatSessionId, state.status(), result);
    }

    private ChatMessageResponse handle(Long userId, Long id, String text, String pregnancyAnswer) {
        long start = System.nanoTime();
        ChatSessionStore.State state = sessionStore.state(userId, id);
        if (state.finished()) {
            throw new BusinessException(ErrorCode.CHAT_ALREADY_FINISHED);
        }
        String pregnancy = state.pregnancy();
        if (state.turnCount() == 0 && pregnancyAnswer != null) {
            pregnancy = validPregnancy(pregnancyAnswer);
            String answered = pregnancy;
            sessionStore.update(id, s -> s.answerPregnancy(answered));
        }

        boolean answering = state.answeringClarification();
        ChatParse.Clarification shown = answering ? readClarification(state.clarificationQuestion()) : null;
        ChatParser.Parsed parsed;
        try {
            parsed = timed("parse", () -> answering
                    ? parser.parse(state.firstText(), shown == null ? "" : shown.question(), text)
                    : parser.parse(text, null, null));
        } catch (ChatParseException e) {
            countResult("error");
            throw new BusinessException(ErrorCode.CHAT_PARSE_FAILED);
        }
        ChatParse parse = parsed.parse();
        int turnNo = state.turnCount() + 1;
        String raw = parsed.raw() == null ? null : parsed.raw().toString();
        String logIds = json(parsed.logIds());

        if (parse.hasRedFlag()) {
            sessionStore.saveTurn(id, turnNo, text, raw, null, logIds);
            sessionStore.update(id, AiChatSession::markRedFlag);
            countResult("red_flag");
            return ChatMessageResponse.redFlag(id, parse.redFlag(), RedFlagDetector.notice(parse.redFlag()));
        }
        if (parse.offTopic()) {
            sessionStore.saveTurn(id, turnNo, text, raw, null, logIds);
            countResult("off_topic");
            return ChatMessageResponse.offTopic(id);
        }
        // 되묻기는 최대 1번 (D5). 되묻기 답 이후에는 ChatParser가 needs_clarification을 지운다
        if (parse.needsClarification() && !state.clarified() && parse.clarification() != null) {
            sessionStore.saveTurn(id, turnNo, text, raw, json(parse.clarification()), logIds);
            sessionStore.update(id, AiChatSession::markClarifying);
            countResult("clarify");
            return ChatMessageResponse.clarify(id, parse.clarification().question(), parse.clarification().options());
        }

        // ② 안전 입력: 프로필 + 대화 속 약·질환 (세션에 저장, 프로필에는 저장하지 않음)
        ChatInputAssembler.Extracted extracted = chatInputAssembler.match(parse, parsed.drugs());
        RecommendationInput input = chatInputAssembler.assemble(userId, pregnancy, extracted);
        sessionStore.saveTurn(id, turnNo, text, raw, null, logIds);
        String meds = chatInputAssembler.writeMedications(extracted.medications());
        String conds = chatInputAssembler.writeConditions(extracted.conditions());
        sessionStore.update(id, s -> s.markSearching(meds, conds));
        String medicationNotice = extracted.hasUnmatched() ? ChatMessageResponse.UNMATCHED_MEDICATION_NOTICE : null;

        // ③ 트랙 1
        List<ReviewVectorIndex.Hit> hits = search(parse.searchQuery());
        ReviewTrack.Result track1 = reviewTrack.run(hits, input);
        int latency = (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        String hitJson = json(track1.hitMentionIds());
        BigDecimal top = track1.topSimilarity() == null ? null
                : BigDecimal.valueOf(track1.topSimilarity()).setScale(4, RoundingMode.HALF_UP);
        String excluded = json(Map.of("items", track1.excludedItems().stream().map(RecommendationResponse.Item::ingredient)
                .toList(), "products", track1.excludedProductCount()));
        if (!track1.empty()) {
            String cards = json(Map.of("cards", track1.cards(), "excludedItems", track1.excludedItems(),
                    "excludedProductCount", track1.excludedProductCount(),
                    "medicationNotice", medicationNotice == null ? "" : medicationNotice));
            sessionStore.saveReviewResult(id, cards, hitJson, top, excluded, vectorIndex.dataVersion(), latency);
            countResult("review");
            return ChatMessageResponse.result(id, track1.cards(), track1.excludedItems(),
                    track1.excludedProductCount(), ReviewProductResponse.MEMBER_NOTICE, medicationNotice);
        }

        // ④ 트랙 2: 대화 원문을 personalizedAnswers로 넣어 기존 파이프라인 (비동기)
        RecommendationInput fallbackInput = input.toBuilder()
                .personalizedAnswers(chatAnswers(answering ? state.firstText() : text, shown, answering ? text : null))
                .chatSessionId(id)
                .build();
        Long recommendationId = sessionStore.startFallback(id, hitJson, top, excluded, vectorIndex.dataVersion(),
                latency);
        fallbackRunner.submit(id, recommendationId, fallbackInput);
        countResult("rule_based");
        return ChatMessageResponse.pending(id, recommendationId, medicationNotice);
    }

    // 검색 문장 임베딩 → τ 이상 언급. 임베딩이 실패하면 트랙 1 결과 없음으로 보고 트랙 2로 (안전 판단은 그대로)
    private List<ReviewVectorIndex.Hit> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        try {
            EmbeddingClient.Embedding e = timed("embed", () -> embeddingClient.embed(query));
            return timed("search", () -> vectorIndex.search(e.vector(), properties.getSearch().getTau(),
                    properties.getSearch().getTopK()));
        } catch (LlmCallException e) {
            log.warn("chat embedding failed, fallback to rule-based: kind={}", e.getKind());
            return List.of();
        }
    }

    /**
     * 트랙 2 입력의 대화 답. 원문·답은 selectedLabels에 넣는다: 결과 캐시 키(CacheKeyFactory)가 topic·question·selectedLabels만
     * 해시하므로 topicLabel에만 두면 서로 다른 고민이 같은 키가 된다
     */
    public static List<RecommendationInput.PersonalizedAnswer> chatAnswers(String firstText, ChatParse.Clarification shown,
                                                                    String answer) {
        List<RecommendationInput.PersonalizedAnswer> out = new ArrayList<>();
        out.add(new RecommendationInput.PersonalizedAnswer(CHAT_TOPIC, "대화: " + firstText, CHAT_QUESTION,
                List.of(firstText)));
        if (shown != null && answer != null) {
            out.add(new RecommendationInput.PersonalizedAnswer(CHAT_TOPIC, "대화: " + firstText, shown.question(),
                    List.of(answer)));
        }
        return List.copyOf(out);
    }

    private ChatMessageResponse snapshot(Long id, AiChatResult r) {
        if (AiChatResult.RULE_BASED.equals(r.getTrack())) {
            return ChatMessageResponse.pending(id, r.getRecommendationId(), null);
        }
        JsonNode root = objectMapper.readTree(r.getCards());
        List<ChatCard> cards = objectMapper.readValue(root.path("cards").toString(), new TypeReference<>() {
        });
        List<RecommendationResponse.Item> excluded = objectMapper.readValue(root.path("excludedItems").toString(),
                new TypeReference<>() {
                });
        String notice = root.path("medicationNotice").asString("");
        return ChatMessageResponse.result(id, cards, excluded, root.path("excludedProductCount").asInt(0),
                ReviewProductResponse.MEMBER_NOTICE, notice.isEmpty() ? null : notice);
    }

    private static String validPregnancy(String code) {
        return PregnancyQuestion.OPTIONS.stream().map(o -> o.code()).filter(code::equals).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_INVALID_INPUT));
    }

    private ChatParse.Clarification readClarification(String json) {
        if (json == null) {
            return null;
        }
        return objectMapper.readValue(json, ChatParse.Clarification.class);
    }

    private String json(Object value) {
        return objectMapper.writeValueAsString(value instanceof Map<?, ?> m ? new LinkedHashMap<>(m) : value);
    }

    private <T> T timed(String stage, Supplier<T> work) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return work.get();
        } finally {
            sample.stop(meterRegistry.timer(REQUEST_METRIC, "stage", stage));
        }
    }

    private void countResult(String outcome) {
        meterRegistry.counter(RESULT_METRIC, "outcome", outcome).increment();
    }
}
