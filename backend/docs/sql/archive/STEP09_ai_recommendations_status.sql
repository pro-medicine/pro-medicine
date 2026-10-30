-- STEP 09: 비동기 처리 상태 컬럼 (엔티티 AiRecommendation)
-- 로컬은 ddl-auto: update로 자동 반영된다 (기존 행은 기본값 DONE). 공유 DB에는 수동 실행.
ALTER TABLE `ai_recommendations`
    ADD COLUMN `status`      VARCHAR(10) NOT NULL DEFAULT 'DONE' COMMENT 'PENDING, PROCESSING, DONE, FAILED',
    ADD COLUMN `started_at`  DATETIME    NULL,
    ADD COLUMN `finished_at` DATETIME    NULL,
    ADD COLUMN `error_code`  VARCHAR(30) NULL;
