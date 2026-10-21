-- STEP 06: 성분 기준값 테이블 + 추천 항목 용량 컬럼
-- 로컬은 ddl-auto: update로 자동 반영된다. 공유 DB에는 수동 실행. 시드는 STEP06_seed_ingredient_standards.sql
CREATE TABLE `ingredient_standards` (
    `standard_id`        BIGINT        NOT NULL AUTO_INCREMENT,
    `ingredient_code`    VARCHAR(30)   NOT NULL,
    `gender`             VARCHAR(5)    NOT NULL COMMENT 'M, F, ALL',
    `age_min`            INT           NOT NULL,
    `age_max`            INT           NULL COMMENT '상한 없으면 NULL',
    `condition_code`     VARCHAR(30)   NULL COMMENT 'SMOKING_CURRENT 등 (STEP 07 규칙과 같은 코드)',
    `recommended_amount` DECIMAL(10,2) NULL,
    `upper_limit`        DECIMAL(10,2) NULL,
    `unit`               VARCHAR(10)   NOT NULL,
    `standard_version`   VARCHAR(20)   NOT NULL COMMENT 'KDRI-2025, NIH-ODS',
    `source_id`          VARCHAR(10)   NOT NULL COMMENT 'domain-rules.md의 S-xx',
    PRIMARY KEY (`standard_id`)
);

ALTER TABLE `ai_recommendation_items`
    ADD COLUMN `recommended_amount` DECIMAL(10,2) NULL,
    ADD COLUMN `current_intake`     DECIMAL(10,2) NULL,
    ADD COLUMN `intake_status`      VARCHAR(10)   NULL COMMENT 'KNOWN, UNKNOWN',
    ADD COLUMN `unit`               VARCHAR(10)   NULL,
    ADD COLUMN `dose_note`          VARCHAR(200)  NULL COMMENT '상한 조정, 기준값 미등록 등';
