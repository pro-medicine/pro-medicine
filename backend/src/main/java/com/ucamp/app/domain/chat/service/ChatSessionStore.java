package com.ucamp.app.domain.chat.service;

import com.ucamp.app.domain.chat.entity.AiChatResult;
import com.ucamp.app.domain.chat.entity.AiChatSession;
import com.ucamp.app.domain.chat.entity.AiChatTurn;
import com.ucamp.app.domain.chat.repository.AiChatResultRepository;
import com.ucamp.app.domain.chat.repository.AiChatSessionRepository;
import com.ucamp.app.domain.chat.repository.AiChatTurnRepository;
import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;
import com.ucamp.app.domain.recommendation.service.RecommendationStore;
import com.ucamp.app.domain.user.service.UserConsentService;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 대화 세션 저장·조회. LLM·임베딩 호출 중에는 트랜잭션을 잡지 않도록 저장만 짧게 따로 둔다 (RecommendationStore와 같은 방식)
 */
@Service
@RequiredArgsConstructor
public class ChatSessionStore {
    private final AiChatSessionRepository sessionRepository;
    private final AiChatTurnRepository turnRepository;
    private final AiChatResultRepository resultRepository;
    private final RecommendationStore recommendationStore;
    private final UserConsentService userConsentService;

    /**
     * 화면 처리에 필요한 세션 값 (엔티티 대신)
     *
     * @param firstText             첫 입력 (되묻기 답일 때 같이 보낸다)
     * @param clarificationQuestion 마지막으로 보여준 되묻기 질문
     */
    public record State(Long chatSessionId, Long userId, String status, String pregnancy, boolean clarified,
                        int turnCount, String firstText, String clarificationQuestion) {
        public boolean finished() {
            return AiChatSession.FINISHED.contains(status) || AiChatSession.FAILED.equals(status)
                    || AiChatSession.SEARCHING.equals(status);
        }

        public boolean answeringClarification() {
            return clarified && AiChatSession.CLARIFYING.equals(status);
        }
    }

    @Transactional
    public Long start(Long userId) {
        return sessionRepository.save(AiChatSession.start(userConsentService.getUser(userId))).getChatSessionId();
    }

    /** 내 세션만. 남의 세션·없는 세션은 같은 404 (V2 회차와 같은 처리) */
    @Transactional(readOnly = true)
    public State state(Long userId, Long chatSessionId) {
        AiChatSession s = owned(userId, chatSessionId);
        List<AiChatTurn> turns = turnRepository.findByChatSession_ChatSessionIdOrderByTurnNoAsc(chatSessionId);
        String first = turns.isEmpty() ? null : turns.get(0).getUserText();
        String question = turns.stream().map(AiChatTurn::getClarification).filter(c -> c != null)
                .reduce((a, b) -> b).orElse(null);
        return new State(s.getChatSessionId(), userId, s.getStatus(), s.getPregnancy(), s.isClarified(), turns.size(),
                first, question);
    }

    @Transactional(readOnly = true)
    public Optional<AiChatResult> result(Long chatSessionId) {
        return resultRepository.findByChatSession_ChatSessionId(chatSessionId);
    }

    @Transactional
    public void saveTurn(Long chatSessionId, int turnNo, String text, String parseOutput, String clarification,
                         String logIds) {
        AiChatSession s = sessionRepository.getReferenceById(chatSessionId);
        turnRepository.save(AiChatTurn.builder().chatSession(s).turnNo(turnNo).userText(text)
                .parseOutput(parseOutput).clarification(clarification).llmCallLogIds(logIds).build());
    }

    @Transactional
    public void update(Long chatSessionId, Consumer<AiChatSession> change) {
        AiChatSession s = sessionRepository.findForUpdate(chatSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        change.accept(s);
    }

    /**
     * 트랙 1 결과 저장 + 세션 DONE
     */
    @Transactional
    public void saveReviewResult(Long chatSessionId, String cards, String hitMentionIds, BigDecimal topSimilarity,
                                 String excluded, LocalDateTime dataVersion, int latencyMs) {
        AiChatSession s = sessionRepository.findForUpdate(chatSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        resultRepository.save(AiChatResult.builder().chatSession(s).track(AiChatResult.REVIEW).cards(cards)
                .hitMentionIds(hitMentionIds).topSimilarity(topSimilarity).excluded(excluded).dataVersion(dataVersion)
                .latencyMs(latencyMs).build());
        s.markDone();
    }

    /**
     * 트랙 2: PENDING 추천 행(chat_session_id 연결) + 결과 스냅샷 + 세션 FALLBACK_PENDING. 추천 id를 돌려준다
     */
    @Transactional
    public Long startFallback(Long chatSessionId, String hitMentionIds, BigDecimal topSimilarity, String excluded,
                              LocalDateTime dataVersion, int latencyMs) {
        AiChatSession s = sessionRepository.findForUpdate(chatSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND));
        RecommendationResponse pending = recommendationStore.createPendingForChat(s);
        resultRepository.save(AiChatResult.builder().chatSession(s).track(AiChatResult.RULE_BASED)
                .recommendationId(pending.recommendationId()).hitMentionIds(hitMentionIds).topSimilarity(topSimilarity)
                .excluded(excluded).dataVersion(dataVersion).latencyMs(latencyMs).build());
        s.markFallbackPending();
        return pending.recommendationId();
    }

    /** 서버 재시작 등으로 끝나지 못한 트랙 2 세션 */
    @Transactional(readOnly = true)
    public List<Long> fallbackPendingIds() {
        return sessionRepository.findByStatusIn(List.of(AiChatSession.FALLBACK_PENDING)).stream()
                .map(AiChatSession::getChatSessionId).toList();
    }

    private AiChatSession owned(Long userId, Long chatSessionId) {
        return sessionRepository.findByChatSessionIdAndUser_Id(chatSessionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_SESSION_NOT_FOUND));
    }
}
