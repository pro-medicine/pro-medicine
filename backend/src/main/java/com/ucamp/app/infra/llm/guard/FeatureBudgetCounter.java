package com.ucamp.app.infra.llm.guard;

import com.ucamp.app.infra.llm.log.LlmCallLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 기능별 일일 예산 메모리 카운터 (V5 보안). Redis와 별도로 서버 메모리에서도 카운트한다.
 * LlmGuard.isFeatureBudgetExceeded()는 max(Redis, 메모리)를 쓴다 → Redis 장애 시에도 예산이 작동한다.
 * 날짜 기준은 Asia/Seoul (Redis dayKey와 동일).
 */
@Slf4j
public class FeatureBudgetCounter {
    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final LlmCallLogRepository logRepository;
    private final ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();
    private volatile String currentDate = todayString();

    public FeatureBudgetCounter(LlmCallLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /** LLM/임베딩 호출 시 1 증가 */
    public long increment(String feature) {
        rolloverIfNeeded();
        return counts.computeIfAbsent(feature, k -> new AtomicLong()).incrementAndGet();
    }

    /** 현재 메모리 카운트 조회 */
    public long get(String feature) {
        rolloverIfNeeded();
        AtomicLong counter = counts.get(feature);
        return counter != null ? counter.get() : 0;
    }

    /** 서버 시작 시 오늘(Asia/Seoul) llm_call_logs에서 기능별 건수로 초기화 */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            LocalDateTime since = LocalDate.now(ZONE).atStartOfDay();
            logRepository.countByFeatureSince(since).forEach(row -> {
                String feature = (String) row[0];
                long count = (Long) row[1];
                counts.computeIfAbsent(feature, k -> new AtomicLong()).addAndGet(count);
            });
            log.info("FeatureBudgetCounter initialized: {}", counts);
        } catch (Exception e) {
            log.warn("FeatureBudgetCounter init failed (will start from 0): {}", e.getMessage());
        }
    }

    private void rolloverIfNeeded() {
        String today = todayString();
        if (!today.equals(currentDate)) {
            synchronized (this) {
                if (!today.equals(currentDate)) {
                    counts.clear();
                    currentDate = today;
                    log.info("FeatureBudgetCounter rolled over to {}", today);
                }
            }
        }
    }

    private static String todayString() {
        return LocalDate.now(ZONE).toString();
    }
}
