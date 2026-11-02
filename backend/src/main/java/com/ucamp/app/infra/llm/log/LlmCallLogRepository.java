package com.ucamp.app.infra.llm.log;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LlmCallLogRepository extends JpaRepository<LlmCallLog, Long> {
    List<LlmCallLog> findByEvalRunIdOrderByLlmCallLogIdAsc(String evalRunId);

    List<LlmCallLog> findByResponseIdOrderByLlmCallLogIdAsc(Long responseId);

    /** V5: 서버 시작 시 FeatureBudgetCounter 초기화용 — 오늘(since) 이후 기능별 호출 건수 */
    @Query("SELECT l.feature, COUNT(l) FROM LlmCallLog l WHERE l.feature IS NOT NULL AND l.createdAt >= :since GROUP BY l.feature")
    List<Object[]> countByFeatureSince(@Param("since") LocalDateTime since);
}
