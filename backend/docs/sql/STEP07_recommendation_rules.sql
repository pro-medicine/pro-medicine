-- STEP 07: 추천 규칙 테이블 + 추천 항목 규칙 검증 컬럼
-- 로컬은 ddl-auto: update로 자동 반영된다. 공유 DB에는 수동 실행. 시드는 STEP07_seed_recommendation_rules.sql
CREATE TABLE `recommendation_rules` (
    `rule_id`           VARCHAR(10)  NOT NULL,
    `condition_type`    VARCHAR(15)  NOT NULL COMMENT 'HABIT, DRUG, STATUS, INGREDIENT',
    `condition_code`    VARCHAR(40)  NOT NULL,
    `target_ingredient` VARCHAR(30)  NOT NULL,
    `action`            VARCHAR(10)  NOT NULL COMMENT 'EXCLUDE, WARN, TIMING, ADJUST, ASK',
    `message_ko`        VARCHAR(300) NOT NULL,
    `guest_excluded`    BOOLEAN      NOT NULL DEFAULT FALSE,
    `source_id`         VARCHAR(20)  NOT NULL,
    `active`            BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (`rule_id`)
);

ALTER TABLE `ai_recommendation_items`
    ADD COLUMN `excluded`         BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN `applied_rule_ids` VARCHAR(200) NULL COMMENT '쉼표 구분',
    ADD COLUMN `warnings`         TEXT         NULL COMMENT 'JSON 배열';
