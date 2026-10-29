package com.ucamp.app.domain.chat.service;

import com.ucamp.app.domain.chat.config.ChatProperties;
import com.ucamp.app.domain.chat.entity.AiChatSession;
import com.ucamp.app.domain.recommendation.config.RecommendationProperties;
import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationResult;
import com.ucamp.app.domain.recommendation.service.RecommendationService;
import com.ucamp.app.domain.recommendation.service.RecommendationStore;
import com.ucamp.app.global.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 트랙 2(기존 성분 추천으로 폴백) 비동기 실행. V1 큐 워커는 설문 응답이 있는 행만 처리하므로(RecommendationStore.markProcessing)
 * 대화창은 이 실행기로 기존 파이프라인(RecommendationService.runInput)을 돌리고 store.complete·fail로 끝낸다.
 * 화면은 기존 GET /api/v1/recommendations/{id}를 폴링한다.
 * 서버가 다시 뜨면 끝나지 못한 대화 추천은 FAILED로 정리한다 (재실행하지 않음: 사용자가 새 대화로 다시 시작)
 */
@Slf4j
@Component
public class ChatFallbackRunner {
    static final String INTERRUPTED = "INTERRUPTED";
    static final String INTERNAL = "INTERNAL";

    private final RecommendationService recommendationService;
    private final RecommendationStore recommendationStore;
    private final RecommendationProperties recommendationProperties;
    private final ChatSessionStore sessionStore;
    private final ChatProperties chatProperties;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "chat-fallback");
        t.setDaemon(true);
        return t;
    });

    public ChatFallbackRunner(RecommendationService recommendationService, RecommendationStore recommendationStore,
                              RecommendationProperties recommendationProperties, ChatSessionStore sessionStore,
                              ChatProperties chatProperties) {
        this.recommendationService = recommendationService;
        this.recommendationStore = recommendationStore;
        this.recommendationProperties = recommendationProperties;
        this.sessionStore = sessionStore;
        this.chatProperties = chatProperties;
    }

    /** input은 chatSessionId·personalizedAnswers가 채워진 트랙 2 입력 */
    public void submit(Long chatSessionId, Long recommendationId, RecommendationInput input) {
        if (!chatProperties.getFallback().isAsync()) {
            run(chatSessionId, recommendationId, input);
            return;
        }
        executor.execute(() -> run(chatSessionId, recommendationId, input));
    }

    void run(Long chatSessionId, Long recommendationId, RecommendationInput input) {
        String version = recommendationProperties.getPipelineVersion();
        try {
            RecommendationResult result = recommendationService.runInput(input, version);
            recommendationStore.complete(recommendationId, result);
            sessionStore.update(chatSessionId, AiChatSession::markDone);
        } catch (BusinessException e) {
            fail(chatSessionId, recommendationId, e.getErrorCode().getCode());
        } catch (RuntimeException e) {
            log.warn("chat fallback failed: chatSession={}, error={}", chatSessionId, e.getClass().getSimpleName());
            fail(chatSessionId, recommendationId, INTERNAL);
        }
    }

    private void fail(Long chatSessionId, Long recommendationId, String code) {
        try {
            recommendationStore.fail(recommendationId, code);
            sessionStore.update(chatSessionId, AiChatSession::markFailed);
        } catch (RuntimeException e) {
            log.warn("chat fallback fail-marking failed: chatSession={}, error={}", chatSessionId,
                    e.getClass().getSimpleName());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cleanUpInterrupted() {
        try {
            for (Long id : sessionStore.fallbackPendingIds()) {
                sessionStore.result(id).map(r -> r.getRecommendationId()).ifPresent(recId -> {
                    RecommendationResponse rec = recommendationStore.get(recId);
                    if (AiRecommendation.PENDING.equals(rec.status()) || AiRecommendation.PROCESSING.equals(rec.status())) {
                        recommendationStore.fail(recId, INTERRUPTED);
                    }
                });
                sessionStore.update(id, AiChatSession::markFailed);
            }
        } catch (RuntimeException e) {
            // ai_chat_sessions가 아직 없는 DB(AI_SETUP 실행 전) 등
            log.warn("chat fallback cleanup skipped: {}", e.getClass().getSimpleName());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
