package com.ucamp.app.domain.recommendation.service;

import com.ucamp.app.domain.chat.dto.ChatCard;
import com.ucamp.app.domain.chat.entity.AiChatResult;
import com.ucamp.app.domain.chat.repository.AiChatResultRepository;
import com.ucamp.app.domain.recommendation.dto.RecentRecommendationResponse;
import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationRepository;
import com.ucamp.app.domain.review.dto.ReviewProductResponse;
import com.ucamp.app.domain.review.service.ReviewProductRecommendationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * V7 마이페이지 "최근 추천 제품 보기": 회원의 가장 최근 추천 1건 (성분 카드 + 성분별 제품). LLM·임베딩을 부르지 않는다.
 * <ul>
 *   <li>후보 ① ai_recommendations DONE (V1 설문·V2 맞춤 질문·V4 트랙 2) — 완료 시각(finished_at) 기준</li>
 *   <li>후보 ② ai_chat_results 트랙 1(REVIEW) — created_at(결과 저장 = 완료 시각) 기준</li>
 *   <li>더 최근 것 1건. 같으면 ①. 트랙 2는 ①에서만 센다 (중복 없음)</li>
 * </ul>
 * 제품: ①은 V3 제품 API와 같은 소유권 확인(RecommendationAccess) + ReviewProductRecommendationService.recommend
 * (조건 로더·안전 필터 그대로)로 지금 데이터 기준 다시 계산한다. ②는 저장된 카드 스냅샷 그대로 (V4 "다시 열면 같은 결과").
 * <p>
 * 트랜잭션을 걸지 않는다: 성분 하나의 제품 조회가 예외를 던져도 다른 성분·응답 전체가 롤백 전용으로 묶이지 않게
 * 각 호출이 자기 트랜잭션을 쓴다. 엔티티는 FK 존재 여부·id만 읽는다 (지연 로딩 없음).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecentRecommendationService {
    private static final PageRequest FIRST = PageRequest.of(0, 1);

    private final AiRecommendationRepository recommendationRepository;
    private final AiChatResultRepository chatResultRepository;
    private final RecommendationStore recommendationStore;
    private final RecommendationAccess recommendationAccess;
    private final ReviewProductRecommendationService productService;
    private final ObjectMapper objectMapper;

    public RecentRecommendationResponse recent(Long userId) {
        AiRecommendation rec = recommendationRepository.findRecentDoneByMember(userId, FIRST).stream()
                .findFirst().orElse(null);
        AiChatResult chat = chatResultRepository.findRecentReviewByUserId(userId, FIRST).stream()
                .findFirst().orElse(null);
        if (rec == null && chat == null) {
            return RecentRecommendationResponse.none();
        }
        if (chat == null || (rec != null && !completedAt(rec).isBefore(chat.getCreatedAt()))) {
            return fromRecommendation(userId, rec);
        }
        return fromChatResult(chat);
    }

    private static LocalDateTime completedAt(AiRecommendation rec) {
        return rec.getFinishedAt() != null ? rec.getFinishedAt() : rec.getCreatedAt();
    }

    // ① V1·V2·V4 트랙 2: 성분은 저장된 추천 순서, 제품은 V3 서비스로 (안전 필터 포함)
    private RecentRecommendationResponse fromRecommendation(Long userId, AiRecommendation rec) {
        Long recId = rec.getRecommendationId();
        // V3 제품 API와 같은 본인 확인 (쿼리가 이미 회원으로 골랐지만 지름길을 만들지 않는다)
        recommendationAccess.requireOwned(userId, recId, null);
        RecommendationResponse saved = recommendationStore.get(recId);
        List<ChatCard> cards = new ArrayList<>();
        for (RecommendationResponse.Item item : saved.items()) {
            cards.add(new ChatCard(item.ingredient(), item.reason(), item.sortOrder(), item.amount(), item.unit(),
                    item.currentIntake(), item.intakeStatus(), item.doseNote(), item.warnings(),
                    products(recId, item.ingredient())));
        }
        Long chatSessionId = rec.getChatSession() != null ? rec.getChatSession().getChatSessionId() : null;
        return new RecentRecommendationResponse(true, source(rec), completedAt(rec), recId, chatSessionId,
                ReviewProductResponse.MEMBER_NOTICE, List.copyOf(cards));
    }

    // 성분 하나의 제품 조회가 실패해도 응답 전체를 실패시키지 않는다: 그 성분만 빈 목록 + 경고 로그
    private List<ReviewProductResponse.Product> products(Long recId, String ingredient) {
        try {
            return productService.recommend(recId, ingredient).products();
        } catch (RuntimeException e) {
            log.warn("recent recommendation products failed: recommendationId={}, ingredient={}, error={}",
                    recId, ingredient, e.toString());
            return List.of();
        }
    }

    private static String source(AiRecommendation rec) {
        if (rec.getResponse() != null) {
            return RecentRecommendationResponse.V1_SURVEY;
        }
        if (rec.getSession() != null) {
            return RecentRecommendationResponse.V2_PERSONALIZED;
        }
        return RecentRecommendationResponse.V4_CHAT;
    }

    // ② V4 트랙 1: 저장된 카드 스냅샷 그대로 (ChatRecommendationService.snapshot과 같은 형식)
    private RecentRecommendationResponse fromChatResult(AiChatResult chat) {
        JsonNode root = objectMapper.readTree(chat.getCards());
        List<ChatCard> cards = objectMapper.readValue(root.path("cards").toString(), new TypeReference<>() {
        });
        return new RecentRecommendationResponse(true, RecentRecommendationResponse.V4_CHAT, chat.getCreatedAt(), null,
                chat.getChatSession().getChatSessionId(), ReviewProductResponse.MEMBER_NOTICE, cards);
    }
}
