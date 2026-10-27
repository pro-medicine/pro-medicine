package com.ucamp.app.infra.llm.log;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// 모든 LLM 호출 기록. 비용·지연 측정의 원본 데이터. 프롬프트·응답 본문은 저장하지 않는다
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "llm_call_logs", indexes = @Index(name = "idx_llm_call_logs_eval_run_id", columnList = "eval_run_id"))
@EntityListeners(AuditingEntityListener.class)
public class LlmCallLog {

    public static final String SUCCESS = "SUCCESS";
    public static final String PARSE_FAIL = "PARSE_FAIL";
    public static final String VALIDATION_FAIL = "VALIDATION_FAIL";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String HTTP_ERROR = "HTTP_ERROR";
    /** 응답은 받았지만 쓰지 않음 (V2 질문 이중 요청에서 진 쪽). 비용은 그대로 기록 */
    public static final String DISCARDED = "DISCARDED";
    public static final int MODEL_LENGTH = 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "llm_call_log_id")
    private Long llmCallLogId;

    // survey_responses 참조. 평가 실행 중이면 null (FK는 걸지 않는다: 기록용 테이블)
    @Column(name = "response_id")
    private Long responseId;

    @Column(name = "eval_run_id", length = 50)
    private String evalRunId;

    // STEP 14 추가: V2 맞춤 질문 회차(ai_question_sessions) 참조. V2 호출은 response_id가 없다 (FK 없음: 기록용 테이블)
    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "pipeline_version", nullable = false, length = 10)
    private String pipelineVersion;

    @Column(name = "call_tag", nullable = false, length = 30)
    private String callTag;

    @Column(name = "model", nullable = false, length = MODEL_LENGTH)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 20)
    private String promptVersion;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "cache_read_tokens")
    private Integer cacheReadTokens;

    @Column(name = "cache_creation_tokens")
    private Integer cacheCreationTokens;

    @Column(name = "latency_ms", nullable = false)
    private Integer latencyMs;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome;

    @Column(name = "http_status")
    private Integer httpStatus;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public LlmCallLog(Long responseId, String evalRunId, Long sessionId, String pipelineVersion, String callTag, String model,
                      String promptVersion, Integer inputTokens, Integer outputTokens, Integer cacheReadTokens,
                      Integer cacheCreationTokens, Integer latencyMs, Integer attemptNo, String outcome,
                      Integer httpStatus) {
        this.responseId = responseId;
        this.evalRunId = evalRunId;
        this.sessionId = sessionId;
        this.pipelineVersion = pipelineVersion;
        this.callTag = callTag;
        this.model = model;
        this.promptVersion = promptVersion;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.cacheReadTokens = cacheReadTokens;
        this.cacheCreationTokens = cacheCreationTokens;
        this.latencyMs = latencyMs;
        this.attemptNo = attemptNo != null ? attemptNo : 1;
        this.outcome = outcome;
        this.httpStatus = httpStatus;
    }

    // 파이프라인이 파싱·검증 실패를 확인한 뒤 SUCCESS를 PARSE_FAIL / VALIDATION_FAIL로 바꾼다
    public void changeOutcome(String outcome) {
        this.outcome = outcome;
    }
}
