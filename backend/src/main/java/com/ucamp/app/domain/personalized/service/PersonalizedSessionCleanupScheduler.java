package com.ucamp.app.domain.personalized.service;

import com.ucamp.app.domain.personalized.repository.AiQuestionSessionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 맞춤 질문 회차 기록 90일 보관 (설계 D15, 8-4). 매일 04:00에 started_at이 90일 지난 회차를 지운다.
 * 질문(ai_question_turns)은 DB CASCADE로 함께 지워지고, 정밀 추천 결과(ai_recommendations)는 남고 session_id만 NULL이 된다.
 * @Scheduled는 SchedulingConfig(notification.scheduler.enabled)가 켜져 있을 때만 돈다 (테스트에서는 꺼짐).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersonalizedSessionCleanupScheduler {
    public static final int RETENTION_DAYS = 90;

    private final AiQuestionSessionRepository sessionRepository;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "0 0 4 * * *")
    public void purgeExpired() {
        purge(LocalDateTime.now());
    }

    /** now 기준 90일 전보다 먼저 시작한 회차 삭제. 삭제 건수를 돌려준다 */
    @Transactional
    public int purge(LocalDateTime now) {
        int deleted = sessionRepository.deleteStartedBefore(now.minusDays(RETENTION_DAYS));
        meterRegistry.counter("pq.session.purged").increment(deleted);
        log.info("personalized sessions purged: count={}, retentionDays={}", deleted, RETENTION_DAYS);
        return deleted;
    }
}
