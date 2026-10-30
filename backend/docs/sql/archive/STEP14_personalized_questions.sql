-- STEP 14: V2 회원 맞춤 질문 → 정밀 성분 추천 (MySQL)
-- 실행 위치: 각자 PC의 MySQL (yakssok DB 선택 후 실행). MySQL Workbench에서 파일을 열어 전체 실행해도 된다
--   예) mysql -u root -p yakssok < backend/docs/sql/STEP14_personalized_questions.sql
-- 순서: STEP13_ai_survey_setup.sql을 먼저 실행한 DB에 실행한다 (STEP13 → STEP14). 새 DB는 schema.sql에 이미 포함
-- 여러 번 실행해도 안전하다 (IF NOT EXISTS, 컬럼·제약이 없을 때만 추가)
-- 다른 팀원 테이블은 users(FK 대상)만 참조하고 데이터는 바꾸지 않는다
-- 영향받는 기능: AI 추천 결과 저장(ai_recommendations.response_id NULL 허용), LLM 호출 기록(llm_call_logs.session_id)

-- ============================================================
-- 1) 새 테이블: 맞춤 질문 회차·질문 (90일 뒤 서버 스케줄러가 삭제)
-- ============================================================
CREATE TABLE IF NOT EXISTS `ai_question_sessions` (
    `session_id`                 BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`                    BIGINT      NOT NULL,
    `status`                     VARCHAR(15) NOT NULL COMMENT 'IN_PROGRESS, READY, DONE, ABANDONED, FAILED',
    `pregnancy`                  VARCHAR(20) NULL COMMENT 'NOT_APPLICABLE, POSSIBLE, PREGNANT, LACTATING (19~50세 여성만)',
    `question_count`             INT         NOT NULL DEFAULT 0 COMMENT 'LLM 질문 수 (고정 질문 제외)',
    `stopped_by_user`            BOOLEAN     NOT NULL DEFAULT FALSE,
    `recommendation_id`          BIGINT      NULL,
    `baseline_recommendation_id` BIGINT      NULL COMMENT '모니터링용: 비교한 V1 추천',
    `diff_json`                  JSON        NULL COMMENT '모니터링용: V1 대비 추가·제외·순서 변화',
    `error_code`                 VARCHAR(20) NULL,
    `started_at`                 DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `finished_at`                DATETIME    NULL,
    PRIMARY KEY (`session_id`),
    KEY `idx_pq_sessions_user` (`user_id`, `started_at`),
    KEY `idx_pq_sessions_started` (`started_at`),
    CONSTRAINT `fk_pq_sessions_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `ai_question_turns` (
    `turn_id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `session_id`      BIGINT       NOT NULL,
    `turn_no`         INT          NOT NULL COMMENT '0 = 고정 질문(임신), 1부터 LLM 질문',
    `fixed`           BOOLEAN      NOT NULL DEFAULT FALSE,
    `topic`           VARCHAR(60)  NOT NULL,
    `depth`           INT          NOT NULL,
    `question_text`   VARCHAR(200) NOT NULL,
    `answer_type`     VARCHAR(10)  NOT NULL COMMENT 'SINGLE, MULTI',
    `options_json`    JSON         NOT NULL COMMENT '[{"label":"4년 이상","kind":"CONCERN"}, ...] 최대 5개',
    `selected_json`   JSON         NULL COMMENT '[2] 또는 [0,3], 답하기 전 NULL',
    `llm_call_log_id` BIGINT       NULL,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `answered_at`     DATETIME     NULL,
    PRIMARY KEY (`turn_id`),
    UNIQUE KEY `uq_pq_turn` (`session_id`, `turn_no`),
    CONSTRAINT `fk_pq_turns_session` FOREIGN KEY (`session_id`) REFERENCES `ai_question_sessions` (`session_id`) ON DELETE CASCADE
);

-- ============================================================
-- 2) ai_recommendations: V2 정밀 추천은 response_id 없이 session_id로 저장
--    response_id 또는 session_id 중 하나는 있어야 한다 → 엔티티에서 검증
--    (90일 삭제 후 session_id가 NULL이 되므로 DB CHECK는 걸지 않는다)
-- ============================================================
ALTER TABLE `ai_recommendations` MODIFY `response_id` BIGINT NULL;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_recommendations' AND column_name = 'session_id') = 0,
    'ALTER TABLE `ai_recommendations` ADD COLUMN `session_id` BIGINT NULL COMMENT ''V2 맞춤 질문 회차 (90일 삭제 후 NULL)''', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'ai_recommendations' AND constraint_name = 'fk_ai_recommendations_session') = 0,
    'ALTER TABLE `ai_recommendations` ADD CONSTRAINT `fk_ai_recommendations_session` FOREIGN KEY (`session_id`) REFERENCES `ai_question_sessions` (`session_id`) ON DELETE SET NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3) llm_call_logs: V2 호출(질문 생성 PQ_QUESTION·PQ_QUESTION_REPAIR, 정밀 추천)의 회차 기록 (FK 없음: 기록용 테이블)
-- ============================================================
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'llm_call_logs' AND column_name = 'session_id') = 0,
    'ALTER TABLE `llm_call_logs` ADD COLUMN `session_id` BIGINT NULL COMMENT ''ai_question_sessions 참조 (V2 호출만, FK 없음)'' AFTER `eval_run_id`', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 회차당 비용 확인 예시
-- SELECT session_id, COUNT(*) AS calls, SUM(input_tokens) AS input_tokens, SUM(output_tokens) AS output_tokens
--   FROM llm_call_logs WHERE session_id IS NOT NULL GROUP BY session_id;
