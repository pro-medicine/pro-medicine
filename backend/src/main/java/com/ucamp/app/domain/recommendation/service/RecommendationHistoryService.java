package com.ucamp.app.domain.recommendation.service;

import com.ucamp.app.domain.chat.dto.ChatCard;
import com.ucamp.app.domain.chat.entity.AiChatResult;
import com.ucamp.app.domain.chat.entity.AiChatTurn;
import com.ucamp.app.domain.chat.repository.AiChatResultRepository;
import com.ucamp.app.domain.chat.repository.AiChatTurnRepository;
import com.ucamp.app.domain.personalized.entity.AiQuestionTurn;
import com.ucamp.app.domain.personalized.planner.OptionKind;
import com.ucamp.app.domain.personalized.planner.Topic;
import com.ucamp.app.domain.personalized.repository.AiQuestionTurnRepository;
import com.ucamp.app.domain.personalized.service.PersonalizedSessionCleanupScheduler;
import com.ucamp.app.domain.personalized.service.TurnCodec;
import com.ucamp.app.domain.personalized.service.TurnOption;
import com.ucamp.app.domain.recommendation.dto.RecommendationHistoryDetailResponse;
import com.ucamp.app.domain.recommendation.dto.RecommendationHistoryResponse;
import com.ucamp.app.domain.recommendation.dto.RecommendationHistoryResponse.Concern;
import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.entity.AiRecommendationItem;
import com.ucamp.app.domain.recommendation.entity.AiRecommendationProduct;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationItemRepository;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationProductRepository;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationRepository;
import com.ucamp.app.domain.recommendation.repository.RecommendationProductQueryRepository;
import com.ucamp.app.domain.review.dto.ReviewProductResponse;
import com.ucamp.app.domain.review.service.ReviewProductRecommendationService;
import com.ucamp.app.domain.survey.entity.SurveyResponse;
import com.ucamp.app.domain.survey.repository.SurveyAnswerRepository;
import com.ucamp.app.domain.survey.service.SurveyCodes;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * V7.1 마이페이지 "추천 기록": 회원의 최근 추천 20건 (V1 설문·V2 맞춤 질문·V4 대화창, 시간순으로 섞음). LLM·임베딩 호출 없음.
 * <ul>
 *   <li>① ai_recommendations DONE + user_id (V1·V2·V4 트랙 2) — 완료 시각(finished_at)</li>
 *   <li>② ai_chat_results 트랙 1(REVIEW) + user_id — created_at (트랙 2는 ①에서만 센다)</li>
 * </ul>
 * 불편한 점은 원본(V1 Q-GOAL 답, V2 CONCERN_OPEN 질문, V4 대화 입력)에서 읽고, 90일이 지나 지워졌으면 EXPIRED.
 * 원문은 로그에 남기지 않는다 (id·건수만).
 * <p>
 * 상세(제품)는 기록마다 따로: ①은 추천받을 때 저장한 순위(ai_recommendation_products), 없으면(V7.1 이전 추천) V3 서비스로
 * 다시 계산. ②는 저장된 카드 그대로. 둘 다 지금 판매 중단·차단된 제품은 뺀다.
 * 상세에는 트랜잭션을 걸지 않는다 (V7 RecentRecommendationService와 같은 이유: 성분 하나의 제품 계산 실패를 격리)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationHistoryService {
    public static final int LIMIT = 20;

    private final AiRecommendationRepository recommendationRepository;
    private final AiRecommendationItemRepository itemRepository;
    private final AiRecommendationProductRepository productRepository;
    private final RecommendationProductQueryRepository productQueryRepository;
    private final AiChatResultRepository chatResultRepository;
    private final AiChatTurnRepository chatTurnRepository;
    private final AiQuestionTurnRepository questionTurnRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final RecommendationStore recommendationStore;
    private final ReviewProductRecommendationService productService;
    private final TurnCodec turnCodec;
    private final ObjectMapper objectMapper;

    // 목록 한 줄 (정렬용)
    private record Entry(LocalDateTime at, AiRecommendation rec, AiChatResult chat) {
        boolean fromRecommendation() {
            return rec != null;
        }

        long id() {
            return rec != null ? rec.getRecommendationId() : chat.getChatResultId();
        }
    }

    // 완료 시각 최신순, 같으면 ① 먼저, 같은 출처면 id 큰 순
    private static final Comparator<Entry> ORDER = Comparator.comparing(Entry::at, Comparator.reverseOrder())
            .thenComparing(e -> e.fromRecommendation() ? 0 : 1)
            .thenComparing(Entry::id, Comparator.reverseOrder());

    // ---------- 목록 ----------

    @Transactional(readOnly = true)
    public RecommendationHistoryResponse list(Long userId) {
        PageRequest page = PageRequest.of(0, LIMIT);
        List<Entry> entries = new ArrayList<>();
        recommendationRepository.findHistoryDoneByUserId(userId, page)
                .forEach(r -> entries.add(new Entry(completedAt(r), r, null)));
        chatResultRepository.findHistoryReviewByUserId(userId, page)
                .forEach(c -> entries.add(new Entry(c.getCreatedAt(), null, c)));
        entries.sort(ORDER);
        List<Entry> top = entries.subList(0, Math.min(LIMIT, entries.size()));
        if (top.isEmpty()) {
            return new RecommendationHistoryResponse(List.of());
        }

        List<AiRecommendation> recs = top.stream().map(Entry::rec).filter(Objects::nonNull).toList();
        Map<Long, List<String>> ingredients = ingredientsOf(recs);
        Set<Long> chatTrack2 = track2Ids(recs);
        Map<Long, List<String>> goals = goalLabels(recs);
        Map<Long, Concern> v2Concerns = v2Concerns(recs);
        Map<Long, Concern> chatConcerns = chatConcerns(top);
        LocalDateTime expiredBefore = LocalDateTime.now().minusDays(PersonalizedSessionCleanupScheduler.RETENTION_DAYS);

        List<RecommendationHistoryResponse.Item> items = new ArrayList<>();
        for (Entry e : top) {
            if (e.fromRecommendation()) {
                AiRecommendation r = e.rec();
                String source = source(r, chatTrack2.contains(r.getRecommendationId()));
                items.add(new RecommendationHistoryResponse.Item(source, e.at(), r.getRecommendationId(), null,
                        concern(r, goals, v2Concerns, chatConcerns, expiredBefore),
                        ingredients.getOrDefault(r.getRecommendationId(), List.of())));
            } else {
                AiChatResult c = e.chat();
                Long sessionId = c.chatSessionIdOrNull();
                Concern concern = sessionId == null ? Concern.expired()
                        : chatConcerns.getOrDefault(sessionId, Concern.expired());
                items.add(new RecommendationHistoryResponse.Item(RecommendationHistoryResponse.V4_CHAT, e.at(), null,
                        c.getChatResultId(), concern, cardIngredients(c)));
            }
        }
        return new RecommendationHistoryResponse(List.copyOf(items));
    }

    private static LocalDateTime completedAt(AiRecommendation rec) {
        return rec.getFinishedAt() != null ? rec.getFinishedAt() : rec.getCreatedAt();
    }

    // V1 → 설문 응답, V2 → 회차, V4 트랙 2 → 대화 세션. 90일 뒤 FK가 비면 대화 결과 행(트랙 2)이 있는지로 V4/V2를 가른다
    private static String source(AiRecommendation rec, boolean chatTrack2) {
        if (rec.getResponse() != null) {
            return RecommendationHistoryResponse.V1_SURVEY;
        }
        if (rec.getSession() != null) {
            return RecommendationHistoryResponse.V2_PERSONALIZED;
        }
        if (rec.getChatSession() != null || chatTrack2) {
            return RecommendationHistoryResponse.V4_CHAT;
        }
        return RecommendationHistoryResponse.V2_PERSONALIZED;
    }

    private static boolean orphan(AiRecommendation r) {
        return r.getResponse() == null && r.getSession() == null && r.getChatSession() == null;
    }

    private Set<Long> track2Ids(List<AiRecommendation> recs) {
        List<Long> orphans = recs.stream().filter(RecommendationHistoryService::orphan)
                .map(AiRecommendation::getRecommendationId).toList();
        return orphans.isEmpty() ? Set.of() : new HashSet<>(chatResultRepository.findRecommendationIdsIn(orphans));
    }

    private Map<Long, List<String>> ingredientsOf(List<AiRecommendation> recs) {
        Map<Long, List<String>> out = new HashMap<>();
        if (recs.isEmpty()) {
            return out;
        }
        for (AiRecommendationItem i : itemRepository.findIncludedByRecommendationIds(
                recs.stream().map(AiRecommendation::getRecommendationId).toList())) {
            out.computeIfAbsent(i.getRecommendation().getRecommendationId(), k -> new ArrayList<>())
                    .add(i.getIngredientName());
        }
        return out;
    }

    private List<String> cardIngredients(AiChatResult chat) {
        return readCards(chat).stream().sorted(Comparator.comparingInt(ChatCard::sortOrder))
                .map(ChatCard::ingredient).toList();
    }

    // ---------- 불편한 점 ----------

    private Concern concern(AiRecommendation r, Map<Long, List<String>> goals, Map<Long, Concern> v2Concerns,
                            Map<Long, Concern> chatConcerns, LocalDateTime expiredBefore) {
        if (r.getResponse() != null) {
            SurveyResponse response = r.getResponse();
            List<String> labels = goals.getOrDefault(response.getResponseId(), List.of());
            if (!labels.isEmpty()) {
                return Concern.labels(labels);
            }
            LocalDateTime submitted = response.getSubmittedAt();
            return submitted != null && submitted.isBefore(expiredBefore) ? Concern.expired() : Concern.none(null);
        }
        if (r.getSession() != null) {
            return v2Concerns.getOrDefault(r.getSession().getSessionId(), Concern.none(null));
        }
        if (r.getChatSession() != null) {
            return chatConcerns.getOrDefault(r.getChatSession().getChatSessionId(), Concern.expired());
        }
        // 회차·대화 세션이 90일 뒤 삭제됨
        return Concern.expired();
    }

    // V1: 설문 Q-GOAL 선택지 라벨 (응답 id → 라벨)
    private Map<Long, List<String>> goalLabels(List<AiRecommendation> recs) {
        List<Long> responseIds = recs.stream().map(AiRecommendation::getResponse).filter(Objects::nonNull)
                .map(SurveyResponse::getResponseId).distinct().toList();
        Map<Long, List<String>> out = new HashMap<>();
        if (responseIds.isEmpty()) {
            return out;
        }
        for (Object[] row : surveyAnswerRepository.findOptionLabels(SurveyCodes.GOAL, responseIds)) {
            out.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add((String) row[1]);
        }
        return out;
    }

    // V2: 회차의 첫 LLM 질문(CONCERN_OPEN)에서 고른 선택지 (회차 id → 불편한 점). 답이 없으면 NONE(text null)
    private Map<Long, Concern> v2Concerns(List<AiRecommendation> recs) {
        List<Long> sessionIds = recs.stream().map(AiRecommendation::getSession).filter(Objects::nonNull)
                .map(s -> s.getSessionId()).distinct().toList();
        Map<Long, Concern> out = new HashMap<>();
        if (sessionIds.isEmpty()) {
            return out;
        }
        for (AiQuestionTurn t : questionTurnRepository.findBySession_SessionIdInAndTopicOrderByTurnNoAsc(sessionIds,
                Topic.CONCERN_OPEN.code())) {
            Long sessionId = t.getSession().getSessionId();
            if (out.containsKey(sessionId) || t.getSelectedJson() == null) {
                continue;
            }
            out.put(sessionId, v2Concern(t));
        }
        return out;
    }

    private Concern v2Concern(AiQuestionTurn t) {
        List<TurnOption> options = turnCodec.decodeOptions(t.getOptionsJson());
        List<TurnOption> selected = turnCodec.decodeSelected(t.getSelectedJson()).stream()
                .filter(i -> i != null && i >= 0 && i < options.size()).map(options::get).toList();
        List<String> labels = selected.stream().filter(o -> o.kind() == null || !o.kind().isExclusive())
                .map(TurnOption::label).toList();
        if (!labels.isEmpty()) {
            return Concern.labels(labels);
        }
        if (selected.stream().anyMatch(o -> o.kind() == OptionKind.NONE)) {
            return Concern.none(Concern.NONE_TEXT);
        }
        if (selected.stream().anyMatch(o -> o.kind() == OptionKind.UNKNOWN)) {
            return Concern.none(Concern.UNKNOWN_TEXT);
        }
        return Concern.none(null);
    }

    // V4: 대화 세션 id → 결과를 만든 입력 (마지막 턴. 바로 앞 턴이 되묻기면 그 턴이 첫 문장이고 마지막 턴이 되묻기 답).
    // 주제 밖으로 거절된 앞 입력은 빼기 위해 "1번 턴"이 아니라 결과를 만든 턴을 쓴다
    private Map<Long, Concern> chatConcerns(List<Entry> entries) {
        Set<Long> ids = new HashSet<>();
        for (Entry e : entries) {
            if (e.rec() != null && e.rec().getChatSession() != null) {
                ids.add(e.rec().getChatSession().getChatSessionId());
            } else if (e.chat() != null && e.chat().chatSessionIdOrNull() != null) {
                ids.add(e.chat().chatSessionIdOrNull());
            }
        }
        Map<Long, Concern> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        Map<Long, List<AiChatTurn>> bySession = new LinkedHashMap<>();
        for (AiChatTurn t : chatTurnRepository.findByChatSession_ChatSessionIdInOrderByChatSession_ChatSessionIdAscTurnNoAsc(ids)) {
            bySession.computeIfAbsent(t.getChatSession().getChatSessionId(), k -> new ArrayList<>()).add(t);
        }
        bySession.forEach((id, turns) -> {
            AiChatTurn last = turns.get(turns.size() - 1);
            AiChatTurn prev = turns.size() >= 2 ? turns.get(turns.size() - 2) : null;
            out.put(id, prev != null && prev.getClarification() != null
                    ? Concern.text(prev.getUserText(), last.getUserText())
                    : Concern.text(last.getUserText(), null));
        });
        return out;
    }

    // ---------- 상세 ----------

    /** ① V1·V2·V4 트랙 2. 내 완료 추천만 (남의 것·없는 것·끝나지 않은 것은 같은 404) */
    public RecommendationHistoryDetailResponse recommendation(Long userId, Long recommendationId) {
        AiRecommendation rec = recommendationRepository.findById(recommendationId)
                .filter(r -> userId.equals(r.userIdOrNull()) && AiRecommendation.DONE.equals(r.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RECOMMENDATION_NOT_FOUND));
        boolean chatTrack2 = orphan(rec)
                && !chatResultRepository.findRecommendationIdsIn(List.of(recommendationId)).isEmpty();
        RecommendationResponse saved = recommendationStore.get(recommendationId);
        List<AiRecommendationProduct> rows = productRepository.findByRecommendationId(recommendationId);
        boolean snapshot = !rows.isEmpty();
        Map<String, List<ReviewProductResponse.Product>> products = snapshot ? fromSnapshot(rows) : Map.of();

        List<ChatCard> cards = new ArrayList<>();
        for (RecommendationResponse.Item item : saved.items()) {
            List<ReviewProductResponse.Product> list = snapshot
                    ? products.getOrDefault(item.ingredient(), List.of())
                    : recompute(recommendationId, item.ingredient());
            cards.add(new ChatCard(item.ingredient(), item.reason(), item.sortOrder(), item.amount(), item.unit(),
                    item.currentIntake(), item.intakeStatus(), item.doseNote(), item.warnings(), list));
        }
        return new RecommendationHistoryDetailResponse(source(rec, chatTrack2), recommendationId, null, snapshot,
                ReviewProductResponse.MEMBER_NOTICE, List.copyOf(cards));
    }

    /** ② V4 트랙 1. 내 결과만 (남의 것·없는 것은 같은 404). 저장된 카드 그대로, 지금 판매 중단·차단된 제품만 뺀다 */
    public RecommendationHistoryDetailResponse chatResult(Long userId, Long chatResultId) {
        AiChatResult chat = chatResultRepository.findOwnedReview(chatResultId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECOMMENDATION_NOT_FOUND));
        List<ChatCard> cards = readCards(chat);
        Set<Long> ids = new HashSet<>();
        cards.forEach(c -> productsOf(c).forEach(p -> ids.add(p.catalogProductId())));
        Set<Long> displayable = productQueryRepository.findDisplayable(ids).keySet();
        List<ChatCard> filtered = cards.stream().sorted(Comparator.comparingInt(ChatCard::sortOrder))
                .map(c -> new ChatCard(c.ingredient(), c.reason(), c.sortOrder(), c.amount(), c.unit(),
                        c.currentIntake(), c.intakeStatus(), c.doseNote(), c.warnings(),
                        productsOf(c).stream().filter(p -> displayable.contains(p.catalogProductId())).toList()))
                .toList();
        return new RecommendationHistoryDetailResponse(RecommendationHistoryResponse.V4_CHAT, null, chatResultId, true,
                ReviewProductResponse.MEMBER_NOTICE, filtered);
    }

    private static List<ReviewProductResponse.Product> productsOf(ChatCard card) {
        return card.products() == null ? List.of() : card.products();
    }

    // 저장된 순위 → 지금 보여줄 수 있는 제품만 (이름·사진·가격·공통 주의점은 지금 값, 같이 든 성분·경고는 추천 당시 값)
    private Map<String, List<ReviewProductResponse.Product>> fromSnapshot(List<AiRecommendationProduct> rows) {
        List<Long> ids = rows.stream().map(AiRecommendationProduct::catalogProductId).distinct().toList();
        Map<Long, RecommendationProductQueryRepository.Display> display = productQueryRepository.findDisplayable(ids);
        Map<String, List<String>> cautions = productQueryRepository.findCautions(ids);
        Map<String, List<ReviewProductResponse.Product>> out = new LinkedHashMap<>();
        for (AiRecommendationProduct row : rows) {
            String ingredient = row.getItem().getIngredientName();
            List<ReviewProductResponse.Product> list = out.computeIfAbsent(ingredient, k -> new ArrayList<>());
            RecommendationProductQueryRepository.Display d = display.get(row.catalogProductId());
            if (d == null) {
                continue; // D12: 지금 판매 중단·차단
            }
            list.add(new ReviewProductResponse.Product(d.catalogProductId(), d.productName(), d.brand(), d.imageUrl(),
                    d.price(), strings(row.getAlsoContains()),
                    cautions.getOrDefault(RecommendationProductQueryRepository.key(d.catalogProductId(), ingredient),
                            List.of()),
                    strings(row.getWarnings())));
        }
        return out;
    }

    // 저장된 순위가 없는 추천 (V7.1 이전): V7처럼 지금 기준으로 다시 계산. 성분 하나가 실패하면 그 성분만 빈 목록
    private List<ReviewProductResponse.Product> recompute(Long recId, String ingredient) {
        try {
            return productService.recommend(recId, ingredient).products();
        } catch (RuntimeException e) {
            log.warn("recommendation history products failed: recommendationId={}, ingredient={}, error={}",
                    recId, ingredient, e.getClass().getSimpleName());
            return List.of();
        }
    }

    private List<ChatCard> readCards(AiChatResult chat) {
        if (chat.getCards() == null) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(chat.getCards());
        return objectMapper.readValue(root.path("cards").toString(), new TypeReference<List<ChatCard>>() {
        });
    }

    private List<String> strings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(json, new TypeReference<List<String>>() {
        });
    }
}
