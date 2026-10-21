package com.ucamp.app.infra.llm.log;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LlmCallLogRepository extends JpaRepository<LlmCallLog, Long> {
    List<LlmCallLog> findByEvalRunIdOrderByLlmCallLogIdAsc(String evalRunId);

    List<LlmCallLog> findByResponseIdOrderByLlmCallLogIdAsc(Long responseId);
}
