-- STEP 03: LLM 호출 기록 테이블 (엔티티 com.ucamp.app.infra.llm.log.LlmCallLog)
-- 로컬은 ddl-auto: update로 자동 생성된다. 운영·공유 DB에는 이 DDL을 수동 실행한다.
-- 프롬프트·응답 본문은 저장하지 않는다 (민감정보).
CREATE TABLE `llm_call_logs` (
    `llm_call_log_id`       BIGINT       NOT NULL AUTO_INCREMENT,
    `response_id`           BIGINT       NULL COMMENT 'survey_responses 참조 (평가 실행 중이면 null, FK 없음)',
    `eval_run_id`           VARCHAR(50)  NULL,
    `pipeline_version`      VARCHAR(10)  NOT NULL,
    `call_tag`              VARCHAR(30)  NOT NULL COMMENT 'RECOMMEND, RECOMMEND_REPAIR',
    `model`                 VARCHAR(60)  NOT NULL,
    `prompt_version`        VARCHAR(20)  NOT NULL,
    `input_tokens`          INT          NULL,
    `output_tokens`         INT          NULL,
    `cache_read_tokens`     INT          NULL,
    `cache_creation_tokens` INT          NULL,
    `latency_ms`            INT          NOT NULL,
    `attempt_no`            INT          NOT NULL DEFAULT 1 COMMENT '네트워크 재시도(STEP 10)만 증가',
    `outcome`               VARCHAR(20)  NOT NULL COMMENT 'SUCCESS, PARSE_FAIL, VALIDATION_FAIL, TIMEOUT, HTTP_ERROR',
    `http_status`           INT          NULL,
    `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`llm_call_log_id`),
    KEY `idx_llm_call_logs_eval_run_id` (`eval_run_id`)
);
