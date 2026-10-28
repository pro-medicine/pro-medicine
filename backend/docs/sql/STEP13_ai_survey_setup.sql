-- STEP 13: 기존 팀원 로컬 DB를 설문·AI 성분 추천을 쓸 수 있는 상태로 맞추는 셋업 SQL (MySQL)
-- 실행 위치: 각자 PC의 MySQL (yakssok DB 선택 후 실행). MySQL Workbench에서 파일을 열어 전체 실행해도 된다
--   예) mysql -u root -p yakssok < backend/docs/sql/STEP13_ai_survey_setup.sql
-- 이 파일 하나만 실행하면 된다. STEP02·06·07·12·15·16 시드를 따로 실행할 필요 없음 (서버를 먼저 켜지 않아도 동작)
-- 여러 번 실행해도 안전하다 (IF NOT EXISTS, INSERT IGNORE, 조건부 DELETE·UPDATE)
-- 다른 팀원 테이블(users, follows, notifications, intake_*, user_products, catalog_products 등)의 데이터는 건드리지 않는다
-- ※ 시드 파일과 내용이 겹친다. STEP02·06·07·12 시드를 바꾸면 이 파일도 같이 고칠 것
-- ※ 새 DB는 이 파일 대신 schema.sql 실행 → STEP06·STEP07 시드 (backend/README.md)

-- ============================================================
-- 1) 구조
-- ============================================================

-- 1-1) AI 테이블 (STEP 03·06·07). 정의는 schema.sql·엔티티와 같다
CREATE TABLE IF NOT EXISTS `llm_call_logs` (
    `llm_call_log_id`       BIGINT      NOT NULL AUTO_INCREMENT,
    `response_id`           BIGINT      NULL COMMENT 'survey_responses 참조 (평가 실행 중이면 null, FK 없음)',
    `eval_run_id`           VARCHAR(50) NULL,
    `pipeline_version`      VARCHAR(10) NOT NULL,
    `call_tag`              VARCHAR(30) NOT NULL COMMENT 'RECOMMEND, RECOMMEND_REPAIR',
    `model`                 VARCHAR(60) NOT NULL,
    `prompt_version`        VARCHAR(20) NOT NULL,
    `input_tokens`          INT         NULL,
    `output_tokens`         INT         NULL,
    `cache_read_tokens`     INT         NULL,
    `cache_creation_tokens` INT         NULL,
    `latency_ms`            INT         NOT NULL,
    `attempt_no`            INT         NOT NULL DEFAULT 1 COMMENT '네트워크 재시도(STEP 10)만 증가',
    `outcome`               VARCHAR(20) NOT NULL COMMENT 'SUCCESS, PARSE_FAIL, VALIDATION_FAIL, TIMEOUT, HTTP_ERROR',
    `http_status`           INT         NULL,
    `created_at`            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`llm_call_log_id`),
    KEY `idx_llm_call_logs_eval_run_id` (`eval_run_id`)
);

CREATE TABLE IF NOT EXISTS `ingredient_standards` (
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

CREATE TABLE IF NOT EXISTS `recommendation_rules` (
    `rule_id`           VARCHAR(10)  NOT NULL,
    `condition_type`    VARCHAR(15)  NOT NULL COMMENT 'HABIT, DRUG, STATUS, INGREDIENT, DISEASE',
    `condition_code`    VARCHAR(40)  NOT NULL,
    `target_ingredient` VARCHAR(30)  NOT NULL,
    `action`            VARCHAR(10)  NOT NULL COMMENT 'EXCLUDE, WARN, TIMING, ADJUST, ASK',
    `message_ko`        VARCHAR(300) NOT NULL,
    `guest_excluded`    BOOLEAN      NOT NULL DEFAULT FALSE,
    `source_id`         VARCHAR(20)  NOT NULL,
    `active`            BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (`rule_id`)
);

-- 1-2) 추천 항목 용량·규칙 컬럼 (STEP 06·07), 추천 상태 컬럼 (STEP 09). 이미 있으면 건너뛴다
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_recommendation_items' AND column_name = 'recommended_amount') = 0,
    'ALTER TABLE `ai_recommendation_items` ADD COLUMN `recommended_amount` DECIMAL(10,2) NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_recommendation_items' AND column_name = 'current_intake') = 0,
    'ALTER TABLE `ai_recommendation_items` ADD COLUMN `current_intake` DECIMAL(10,2) NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_recommendation_items' AND column_name = 'intake_status') = 0,
    'ALTER TABLE `ai_recommendation_items` ADD COLUMN `intake_status` VARCHAR(10) NULL COMMENT ''KNOWN, UNKNOWN''', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_recommendation_items' AND column_name = 'unit') = 0,
    'ALTER TABLE `ai_recommendation_items` ADD COLUMN `unit` VARCHAR(10) NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_recommendation_items' AND column_name = 'dose_note') = 0,
    'ALTER TABLE `ai_recommendation_items` ADD COLUMN `dose_note` VARCHAR(200) NULL COMMENT ''상한 조정, 기준값 미등록 등''', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_recommendation_items' AND column_name = 'excluded') = 0,
    'ALTER TABLE `ai_recommendation_items` ADD COLUMN `excluded` BOOLEAN NOT NULL DEFAULT FALSE', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_recommendation_items' AND column_name = 'applied_rule_ids') = 0,
    'ALTER TABLE `ai_recommendation_items` ADD COLUMN `applied_rule_ids` VARCHAR(200) NULL COMMENT ''쉼표 구분''', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_recommendation_items' AND column_name = 'warnings') = 0,
    'ALTER TABLE `ai_recommendation_items` ADD COLUMN `warnings` TEXT NULL COMMENT ''JSON 배열''', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_recommendations' AND column_name = 'status') = 0,
    'ALTER TABLE `ai_recommendations` ADD COLUMN `status` VARCHAR(10) NOT NULL DEFAULT ''DONE'' COMMENT ''PENDING, PROCESSING, DONE, FAILED''', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_recommendations' AND column_name = 'started_at') = 0,
    'ALTER TABLE `ai_recommendations` ADD COLUMN `started_at` DATETIME NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_recommendations' AND column_name = 'finished_at') = 0,
    'ALTER TABLE `ai_recommendations` ADD COLUMN `finished_at` DATETIME NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ai_recommendations' AND column_name = 'error_code') = 0,
    'ALTER TABLE `ai_recommendations` ADD COLUMN `error_code` VARCHAR(30) NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1-3) JPA IDENTITY 엔티티(설문·AI 추천·건강)의 PK 자동 증가. 없으면 설문 제출·추천 저장이 실패한다
-- FK가 걸린 PK 컬럼을 MODIFY하면 오류(1833)가 나므로 잠시 FK 검사를 끈다. 다시 실행해도 결과는 같다
SET FOREIGN_KEY_CHECKS = 0;
ALTER TABLE `surveys` MODIFY `survey_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `survey_questions` MODIFY `question_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `question_options` MODIFY `option_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `survey_responses` MODIFY `response_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `survey_answers` MODIFY `answer_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `user_conditions` MODIFY `user_condition_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `user_medications` MODIFY `user_medication_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `ai_recommendations` MODIFY `recommendation_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `ai_recommendation_items` MODIFY `recommendation_item_id` BIGINT NOT NULL AUTO_INCREMENT;
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 2) 옛 예시 정리 (예전 schema.sql이 넣은 설문 CONCERN/DIET/EXERCISE/SMOKING과 그 응답·추천)
--    새 설문(Q-* 문항)만 있는 DB에서는 아무것도 지우지 않는다
-- ============================================================

DROP TEMPORARY TABLE IF EXISTS `tmp_old_surveys`;
CREATE TEMPORARY TABLE `tmp_old_surveys` AS
    SELECT DISTINCT `survey_id` FROM `survey_questions` WHERE `code` IN ('CONCERN', 'DIET', 'EXERCISE', 'SMOKING');
DROP TEMPORARY TABLE IF EXISTS `tmp_old_responses`;
CREATE TEMPORARY TABLE `tmp_old_responses` AS
    SELECT `response_id` FROM `survey_responses` WHERE `survey_id` IN (SELECT `survey_id` FROM `tmp_old_surveys`);

-- FK 순서: 추천 항목 → 추천 → 답변 → 응답 → 선택지 → 문항 → 설문
DELETE FROM `ai_recommendation_items` WHERE `recommendation_id` IN (
    SELECT `recommendation_id` FROM `ai_recommendations` WHERE `response_id` IN (SELECT `response_id` FROM `tmp_old_responses`));
DELETE FROM `ai_recommendations` WHERE `response_id` IN (SELECT `response_id` FROM `tmp_old_responses`);
DELETE FROM `survey_answers` WHERE `response_id` IN (SELECT `response_id` FROM `tmp_old_responses`);
DELETE FROM `survey_responses` WHERE `response_id` IN (SELECT `response_id` FROM `tmp_old_responses`);
DELETE FROM `question_options` WHERE `question_id` IN (
    SELECT `question_id` FROM `survey_questions` WHERE `survey_id` IN (SELECT `survey_id` FROM `tmp_old_surveys`));
DELETE FROM `survey_questions` WHERE `survey_id` IN (SELECT `survey_id` FROM `tmp_old_surveys`);
DELETE FROM `surveys` WHERE `survey_id` IN (SELECT `survey_id` FROM `tmp_old_surveys`);

DROP TEMPORARY TABLE IF EXISTS `tmp_old_responses`;
DROP TEMPORARY TABLE IF EXISTS `tmp_old_surveys`;

-- ============================================================
-- 3) 데이터 (예시 데이터. 시드 파일 내용 그대로, PK가 있으면 INSERT IGNORE)
-- ============================================================

-- 3-1) 설문 v1 (STEP02_seed_survey_v1.sql)
-- 설문 v1 시드 (STEP 02). 예시 데이터: 문항 문구는 바꿔도 되지만 code와 value_code는 이후 STEP이 기준으로 쓴다.
-- MySQL / H2(MySQL 모드) 공용. 운영 DB에는 수동으로 한 번 실행한다 (앱 시작 시 자동으로 넣지 않음).
INSERT IGNORE INTO surveys (survey_id, version, status) VALUES (1, 'v1', 'ACTIVE');
INSERT IGNORE INTO survey_questions (question_id, survey_id, code, question_text, answer_type, profile_key, is_sensitive, guest_visible, display_order) VALUES (1, 1, 'Q-GENDER', '성별을 알려주세요', 'SINGLE', 'gender', FALSE, TRUE, 1);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (1, 1, 'M', '남성', 1);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (2, 1, 'F', '여성', 2);
INSERT IGNORE INTO survey_questions (question_id, survey_id, code, question_text, answer_type, profile_key, is_sensitive, guest_visible, display_order) VALUES (2, 1, 'Q-BIRTH', '출생 연도를 알려주세요', 'NUMBER', 'birth_year', FALSE, TRUE, 2);
INSERT IGNORE INTO survey_questions (question_id, survey_id, code, question_text, answer_type, profile_key, is_sensitive, guest_visible, display_order) VALUES (3, 1, 'Q-SMOKING', '흡연하시나요?', 'SINGLE', 'smoking', FALSE, TRUE, 3);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (3, 3, 'NONE', '안 피움', 1);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (4, 3, 'HALF_PACK', '하루 반 갑 이하', 2);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (5, 3, 'ONE_PACK', '하루 1갑', 3);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (6, 3, 'TWO_PACK_PLUS', '하루 2갑 이상', 4);
INSERT IGNORE INTO survey_questions (question_id, survey_id, code, question_text, answer_type, profile_key, is_sensitive, guest_visible, display_order) VALUES (4, 1, 'Q-DRINKING', '술은 얼마나 자주 드시나요?', 'SINGLE', 'drinking', FALSE, TRUE, 4);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (7, 4, 'NONE', '안 마심', 1);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (8, 4, 'LOW', '가끔', 2);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (9, 4, 'HIGH', '자주', 3);
INSERT IGNORE INTO survey_questions (question_id, survey_id, code, question_text, answer_type, profile_key, is_sensitive, guest_visible, display_order) VALUES (5, 1, 'Q-CAFFEINE', '커피·차는 얼마나 드시나요?', 'SINGLE', 'caffeine', FALSE, TRUE, 5);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (10, 5, 'NONE', '안 마심', 1);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (11, 5, 'LOW', '하루 1~2잔', 2);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (12, 5, 'HIGH', '하루 3잔 이상', 3);
INSERT IGNORE INTO survey_questions (question_id, survey_id, code, question_text, answer_type, profile_key, is_sensitive, guest_visible, display_order) VALUES (6, 1, 'Q-DIET', '어떤 식단을 드시나요?', 'SINGLE', 'diet_type', FALSE, TRUE, 6);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (13, 6, 'OMNI', '일반식', 1);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (14, 6, 'VEGETARIAN', '채식(유제품·달걀 포함)', 2);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (15, 6, 'VEGAN', '비건', 3);
INSERT IGNORE INTO survey_questions (question_id, survey_id, code, question_text, answer_type, profile_key, is_sensitive, guest_visible, display_order) VALUES (7, 1, 'Q-SUN', '햇빛을 얼마나 쬐시나요?', 'SINGLE', 'sun_exposure', FALSE, TRUE, 7);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (16, 7, 'LOW', '거의 실내에 있어요', 1);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (17, 7, 'NORMAL', '보통이에요', 2);
INSERT IGNORE INTO survey_questions (question_id, survey_id, code, question_text, answer_type, profile_key, is_sensitive, guest_visible, display_order) VALUES (8, 1, 'Q-SLEEP', '최근 수면 상태는 어떤가요?', 'SINGLE', NULL, FALSE, TRUE, 8);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (18, 8, 'GOOD', '좋아요', 1);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (19, 8, 'NORMAL', '보통이에요', 2);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (20, 8, 'POOR', '잘 못 자요', 3);
INSERT IGNORE INTO survey_questions (question_id, survey_id, code, question_text, answer_type, profile_key, is_sensitive, guest_visible, display_order) VALUES (9, 1, 'Q-FATIGUE', '최근 피로감은 어느 정도인가요?', 'SINGLE', NULL, FALSE, TRUE, 9);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (21, 9, 'LOW', '거의 없어요', 1);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (22, 9, 'NORMAL', '보통이에요', 2);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (23, 9, 'HIGH', '자주 피곤해요', 3);
INSERT IGNORE INTO survey_questions (question_id, survey_id, code, question_text, answer_type, profile_key, is_sensitive, guest_visible, display_order) VALUES (10, 1, 'Q-GOAL', '원하는 효과를 모두 골라주세요', 'MULTI', NULL, FALSE, TRUE, 10);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (24, 10, 'SLEEP', '수면', 1);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (25, 10, 'FATIGUE', '피로 회복', 2);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (26, 10, 'IMMUNITY', '면역', 3);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (27, 10, 'BONE', '뼈 건강', 4);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (28, 10, 'EYE', '눈 건강', 5);
INSERT IGNORE INTO survey_questions (question_id, survey_id, code, question_text, answer_type, profile_key, is_sensitive, guest_visible, display_order) VALUES (11, 1, 'Q-PREGNANCY', '임신·수유 중이신가요?', 'SINGLE', NULL, TRUE, FALSE, 11);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (29, 11, 'NOT_APPLICABLE', '해당 없음', 1);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (30, 11, 'POSSIBLE', '임신 가능성 있음', 2);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (31, 11, 'PREGNANT', '임신 중', 3);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (32, 11, 'LACTATING', '수유 중', 4);
INSERT IGNORE INTO survey_questions (question_id, survey_id, code, question_text, answer_type, profile_key, is_sensitive, guest_visible, display_order) VALUES (12, 1, 'Q-CONDITIONS', '진단받은 질환을 모두 골라주세요', 'MULTI', 'conditions', TRUE, FALSE, 12);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (33, 12, 'NONE', '없음', 1);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (34, 12, 'HYPERTENSION', '고혈압', 2);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (35, 12, 'DIABETES', '당뇨', 3);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (36, 12, 'THYROID', '갑상선 질환', 4);
INSERT IGNORE INTO question_options (option_id, question_id, value_code, label, display_order) VALUES (37, 12, 'KIDNEY', '신장 질환', 5);
INSERT IGNORE INTO survey_questions (question_id, survey_id, code, question_text, answer_type, profile_key, is_sensitive, guest_visible, display_order) VALUES (13, 1, 'Q-MEDICATIONS', '복용 중인 약을 알려주세요', 'TEXT', 'medications', TRUE, FALSE, 13);
INSERT IGNORE INTO survey_questions (question_id, survey_id, code, question_text, answer_type, profile_key, is_sensitive, guest_visible, display_order) VALUES (14, 1, 'Q-SUPPLEMENTS', '복용 중인 영양제 성분과 함량을 알려주세요', 'TEXT', NULL, FALSE, TRUE, 14);

-- 3-2) 약물 마스터 (STEP02_seed_drugs.sql, 계열 모름 D-U01~U12 포함)
-- 약물 마스터 시드 (STEP 02). 예시 데이터: domain-rules.md의 약물 계열별 대표 약 1개씩.
-- drug_class 값은 recommendation_rules(STEP 07)의 DRUG 조건 코드와 같다.
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-001', '메트포르민', 'METFORMIN');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-002', '오메프라졸', 'PPI');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-003', '에날라프릴', 'ACE_INHIBITOR');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-004', '로사르탄', 'ARB');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-005', '스피로놀락톤', 'K_SPARING_DIURETIC');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-006', '히드로클로로티아지드', 'THIAZIDE_LOOP_DIURETIC');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-007', '와파린', 'WARFARIN');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-008', '레보티록신', 'LEVOTHYROXINE');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-009', '시프로플록사신', 'TETRA_QUINOLONE');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-010', '에스시탈로프람', 'SSRI');
-- 예시 약물 (schema.sql 예시 데이터와 같은 값). STEP 16부터 규칙 대상 (CALCIUM_CHANNEL_BLOCKER: R-30c3, STATIN: R-36)
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-011', '암로디핀', 'CALCIUM_CHANNEL_BLOCKER');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-012', '아토르바스타틴', 'STATIN');
-- 성분 확장 (STEP 16): 새 약물 계열 대표 약. drug_class는 R-30b·R-31·R-33b·R-39의 DRUG 조건 코드와 같다
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-013', '클로피도그렐', 'ANTIPLATELET');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-014', '인슐린', 'INSULIN');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-015', '타크로리무스', 'IMMUNOSUPPRESSANT');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-016', '발프로산', 'ANTICONVULSANT');
-- 약 이름을 모르고 계열만 아는 경우 (STEP 12). drug_class = 계열 모름 코드, 규칙은 R-U*로 WARN/TIMING 위주
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-U01', '혈압약 (이름 모름)', 'ANTIHYPERTENSIVE_UNKNOWN');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-U02', '이뇨제 (이름 모름)', 'DIURETIC_UNKNOWN');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-U03', '당뇨약 (이름 모름)', 'ANTIDIABETIC_UNKNOWN');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-U04', '위산억제제 (이름 모름)', 'ACID_REDUCER_UNKNOWN');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-U05', '항응고제 (이름 모름)', 'ANTICOAGULANT_UNKNOWN');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-U06', '갑상선약 (이름 모름)', 'THYROID_MED_UNKNOWN');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-U07', '항생제 (이름 모름)', 'ANTIBIOTIC_UNKNOWN');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-U08', '항우울제 (이름 모름)', 'ANTIDEPRESSANT_UNKNOWN');
-- 성분 확장 (STEP 16): 계열 모름. drug_class를 대표 약과 같은 계열 코드로 두어 같은 규칙이 걸린다 (항암제는 계열 모름만)
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-U09', '항혈소판제 (이름 모름)', 'ANTIPLATELET');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-U10', '항암제 (이름 모름)', 'CHEMOTHERAPY');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-U11', '면역억제제 (이름 모름)', 'IMMUNOSUPPRESSANT');
INSERT IGNORE INTO drugs (drug_id, name, drug_class) VALUES ('D-U12', '뇌전증약 (이름 모름)', 'ANTICONVULSANT');

-- 3-3) 옛 약물 ID → 새 ID. 복용 약 행은 지우지 않고 새 ID로 옮긴 뒤 옛 약물을 지운다
--      (암로디핀 D-011, 메트포르민 D-001, 아토르바스타틴 D-012, 와파린 D-007)
UPDATE `user_medications` SET `drug_id` = 'D-011' WHERE `drug_id` = 'AMLODIPINE';
UPDATE `user_medications` SET `drug_id` = 'D-001' WHERE `drug_id` = 'METFORMIN';
UPDATE `user_medications` SET `drug_id` = 'D-012' WHERE `drug_id` = 'ATORVASTATIN';
UPDATE `user_medications` SET `drug_id` = 'D-007' WHERE `drug_id` = 'WARFARIN';
DELETE FROM `drugs` WHERE `drug_id` IN ('AMLODIPINE', 'METFORMIN', 'ATORVASTATIN', 'WARFARIN');
-- 약물 계열 코드를 규칙(recommendation_rules.condition_code)과 맞춘다 (옛 값이 남아 있는 경우)
UPDATE `drugs` SET `drug_class` = 'METFORMIN' WHERE `drug_class` = 'BIGUANIDE';
UPDATE `drugs` SET `drug_class` = 'WARFARIN' WHERE `drug_class` = 'ANTICOAGULANT';

-- 3-4) 성분 기준값 (STEP06_seed_ingredient_standards.sql). 같은 기준 행이 없을 때만 넣는다
-- 성분 기준값 시드 (STEP 06). 값은 reference/domain-rules.md 2번 표에서 출처를 확인한 것만 넣는다.
-- TODO(출처 필요)인 값은 행을 만들지 않거나 NULL로 둔다 (지어내지 않음). 나머지 성분은 KDRI-2025 원문 확인 후 추가.
-- 비타민 C (S-03, NIH-ODS). 흡연자는 +35mg (R-01)
INSERT INTO ingredient_standards (ingredient_code, gender, age_min, age_max, condition_code, recommended_amount, upper_limit, unit, standard_version, source_id) SELECT 'VITAMIN_C', 'M', 19, NULL, NULL, 90, 2000, 'mg', 'NIH-ODS', 'S-03' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient_standards WHERE `ingredient_code` <=> 'VITAMIN_C' AND `gender` <=> 'M' AND `age_min` <=> 19 AND `age_max` <=> NULL AND `condition_code` <=> NULL AND `standard_version` <=> 'NIH-ODS');
INSERT INTO ingredient_standards (ingredient_code, gender, age_min, age_max, condition_code, recommended_amount, upper_limit, unit, standard_version, source_id) SELECT 'VITAMIN_C', 'F', 19, NULL, NULL, 75, 2000, 'mg', 'NIH-ODS', 'S-03' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient_standards WHERE `ingredient_code` <=> 'VITAMIN_C' AND `gender` <=> 'F' AND `age_min` <=> 19 AND `age_max` <=> NULL AND `condition_code` <=> NULL AND `standard_version` <=> 'NIH-ODS');
INSERT INTO ingredient_standards (ingredient_code, gender, age_min, age_max, condition_code, recommended_amount, upper_limit, unit, standard_version, source_id) SELECT 'VITAMIN_C', 'M', 19, NULL, 'SMOKING_CURRENT', 125, 2000, 'mg', 'NIH-ODS', 'S-03' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient_standards WHERE `ingredient_code` <=> 'VITAMIN_C' AND `gender` <=> 'M' AND `age_min` <=> 19 AND `age_max` <=> NULL AND `condition_code` <=> 'SMOKING_CURRENT' AND `standard_version` <=> 'NIH-ODS');
INSERT INTO ingredient_standards (ingredient_code, gender, age_min, age_max, condition_code, recommended_amount, upper_limit, unit, standard_version, source_id) SELECT 'VITAMIN_C', 'F', 19, NULL, 'SMOKING_CURRENT', 110, 2000, 'mg', 'NIH-ODS', 'S-03' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient_standards WHERE `ingredient_code` <=> 'VITAMIN_C' AND `gender` <=> 'F' AND `age_min` <=> 19 AND `age_max` <=> NULL AND `condition_code` <=> 'SMOKING_CURRENT' AND `standard_version` <=> 'NIH-ODS');
-- 엽산 (S-11). 상한 1000mcg은 합성 엽산(folic acid) 기준이라 DFE 단위 권장량과 섞지 않아 NULL
INSERT INTO ingredient_standards (ingredient_code, gender, age_min, age_max, condition_code, recommended_amount, upper_limit, unit, standard_version, source_id) SELECT 'FOLATE', 'ALL', 19, NULL, NULL, 400, NULL, 'mcg DFE', 'NIH-ODS', 'S-11' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient_standards WHERE `ingredient_code` <=> 'FOLATE' AND `gender` <=> 'ALL' AND `age_min` <=> 19 AND `age_max` <=> NULL AND `condition_code` <=> NULL AND `standard_version` <=> 'NIH-ODS');
-- 철분 남성 19-50 (S-16). 상한은 TODO(출처 필요)라 NULL
INSERT INTO ingredient_standards (ingredient_code, gender, age_min, age_max, condition_code, recommended_amount, upper_limit, unit, standard_version, source_id) SELECT 'IRON', 'M', 19, 50, NULL, 8, NULL, 'mg', 'NIH-ODS', 'S-16' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient_standards WHERE `ingredient_code` <=> 'IRON' AND `gender` <=> 'M' AND `age_min` <=> 19 AND `age_max` <=> 50 AND `condition_code` <=> NULL AND `standard_version` <=> 'NIH-ODS');
-- 나이아신·비타민 B6 (S-26, S-28, NIH-ODS. KDRI-2025 원문 확보 시 교체). 나이아신 상한 35mg은 보충제 기준 (STEP 16)
INSERT INTO ingredient_standards (ingredient_code, gender, age_min, age_max, condition_code, recommended_amount, upper_limit, unit, standard_version, source_id) SELECT 'NIACIN', 'M', 19, NULL, NULL, 16, 35, 'mg NE', 'NIH-ODS', 'S-26' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient_standards WHERE `ingredient_code` <=> 'NIACIN' AND `gender` <=> 'M' AND `age_min` <=> 19 AND `age_max` <=> NULL AND `condition_code` <=> NULL AND `standard_version` <=> 'NIH-ODS');
INSERT INTO ingredient_standards (ingredient_code, gender, age_min, age_max, condition_code, recommended_amount, upper_limit, unit, standard_version, source_id) SELECT 'NIACIN', 'F', 19, NULL, NULL, 14, 35, 'mg NE', 'NIH-ODS', 'S-26' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient_standards WHERE `ingredient_code` <=> 'NIACIN' AND `gender` <=> 'F' AND `age_min` <=> 19 AND `age_max` <=> NULL AND `condition_code` <=> NULL AND `standard_version` <=> 'NIH-ODS');
INSERT INTO ingredient_standards (ingredient_code, gender, age_min, age_max, condition_code, recommended_amount, upper_limit, unit, standard_version, source_id) SELECT 'VITAMIN_B6', 'ALL', 19, 50, NULL, 1.3, 100, 'mg', 'NIH-ODS', 'S-28' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient_standards WHERE `ingredient_code` <=> 'VITAMIN_B6' AND `gender` <=> 'ALL' AND `age_min` <=> 19 AND `age_max` <=> 50 AND `condition_code` <=> NULL AND `standard_version` <=> 'NIH-ODS');
INSERT INTO ingredient_standards (ingredient_code, gender, age_min, age_max, condition_code, recommended_amount, upper_limit, unit, standard_version, source_id) SELECT 'VITAMIN_B6', 'M', 51, NULL, NULL, 1.7, 100, 'mg', 'NIH-ODS', 'S-28' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient_standards WHERE `ingredient_code` <=> 'VITAMIN_B6' AND `gender` <=> 'M' AND `age_min` <=> 51 AND `age_max` <=> NULL AND `condition_code` <=> NULL AND `standard_version` <=> 'NIH-ODS');
INSERT INTO ingredient_standards (ingredient_code, gender, age_min, age_max, condition_code, recommended_amount, upper_limit, unit, standard_version, source_id) SELECT 'VITAMIN_B6', 'F', 51, NULL, NULL, 1.5, 100, 'mg', 'NIH-ODS', 'S-28' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient_standards WHERE `ingredient_code` <=> 'VITAMIN_B6' AND `gender` <=> 'F' AND `age_min` <=> 51 AND `age_max` <=> NULL AND `condition_code` <=> NULL AND `standard_version` <=> 'NIH-ODS');
-- 오메가3 (S-21). 상한 5000mg은 FDA 권고(EPA+DHA 보충제), 권장량 출처가 없어 NULL (STEP 16)
INSERT INTO ingredient_standards (ingredient_code, gender, age_min, age_max, condition_code, recommended_amount, upper_limit, unit, standard_version, source_id) SELECT 'OMEGA3', 'ALL', 19, NULL, NULL, NULL, 5000, 'mg', 'NIH-ODS', 'S-21' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ingredient_standards WHERE `ingredient_code` <=> 'OMEGA3' AND `gender` <=> 'ALL' AND `age_min` <=> 19 AND `age_max` <=> NULL AND `condition_code` <=> NULL AND `standard_version` <=> 'NIH-ODS');

-- 3-5) 추천 규칙 (STEP07_seed_recommendation_rules.sql, 계열 모름 R-U* 포함)
-- 추천 규칙 시드 (STEP 07). reference/domain-rules.md 3번 표 그대로: 회원 23 + 비회원 4 = 27행, STEP 12에서 계열 모름 12행, STEP 15에서 진단 질환 2행, STEP 16에서 성분 확장 40행(회원 32 + 비회원 8) 추가(총 81행).
-- 서비스 공개 전 약사 검토 필요. MySQL / H2(MySQL 모드) 공용. 운영 DB에는 수동 실행.
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-01', 'HABIT', 'SMOKING_CURRENT', 'VITAMIN_C', 'ADJUST', '흡연자는 비타민 C 필요량이 하루 35mg 많아요', FALSE, 'S-03, S-04', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-02', 'HABIT', 'SMOKING_CURRENT', 'BETA_CAROTENE', 'EXCLUDE', '흡연자는 고용량 베타카로틴 보충제를 피하는 게 좋아요', FALSE, 'S-05, S-06', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-03', 'HABIT', 'DRINKING_HIGH', 'VITAMIN_B1', 'WARN', '음주가 잦으면 비타민 B1이 부족해지기 쉬워요', FALSE, 'S-07', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-04', 'HABIT', 'CAFFEINE_HIGH', 'IRON', 'TIMING', '커피·차와 철분은 시간 간격을 두고 드세요', FALSE, 'S-08', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-05', 'HABIT', 'DIET_VEGAN', 'VITAMIN_B12', 'ASK', '비건 식단은 비타민 B12 보충이 필요한지 확인하세요', FALSE, 'S-09', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-06', 'HABIT', 'SUN_LOW', 'VITAMIN_D', 'ASK', '햇빛 노출이 적으면 비타민 D 부족 위험이 있어요', FALSE, 'S-10', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-07', 'STATUS', 'PREGNANCY_POSSIBLE', 'FOLATE', 'ADJUST', '임신 가능성이 있으면 엽산(folic acid) 하루 400mcg이 권장돼요', FALSE, 'S-11', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-08', 'STATUS', 'PREGNANT_OR_LACTATING', 'VITAMIN_A', 'EXCLUDE', '임신·수유 중에는 고용량 비타민 A(레티놀)를 피하세요', FALSE, 'S-06', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-11', 'DRUG', 'METFORMIN', 'VITAMIN_B12', 'WARN', '메트포르민 장기 복용 시 B12 흡수가 줄 수 있어요', FALSE, 'S-12', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-12', 'DRUG', 'PPI', 'VITAMIN_B12', 'WARN', '위산억제제 장기 복용 시 B12 흡수가 줄 수 있어요', FALSE, 'S-12', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-12b', 'DRUG', 'PPI', 'MAGNESIUM', 'WARN', '위산억제제 장기 복용 시 마그네슘이 줄 수 있어요', FALSE, 'S-13', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-13', 'DRUG', 'ACE_INHIBITOR', 'POTASSIUM', 'EXCLUDE', '혈압약(ACE 억제제) 복용 중 칼륨 보충은 전문가와 상담하세요', FALSE, 'S-14', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-13b', 'DRUG', 'ARB', 'POTASSIUM', 'EXCLUDE', '혈압약(ARB) 복용 중 칼륨 보충은 전문가와 상담하세요', FALSE, 'S-14', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-14', 'DRUG', 'K_SPARING_DIURETIC', 'POTASSIUM', 'EXCLUDE', '칼륨보존 이뇨제와 칼륨 보충은 함께 쓰면 안 돼요', FALSE, 'S-14', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-15', 'DRUG', 'THIAZIDE_LOOP_DIURETIC', 'MAGNESIUM', 'WARN', '이뇨제 복용 시 마그네슘이 빠져나갈 수 있어요', FALSE, 'S-13', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-16', 'DRUG', 'WARFARIN', 'VITAMIN_K', 'WARN', '와파린 복용 중에는 비타민 K 섭취량을 일정하게 유지하세요', FALSE, 'S-15', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-17', 'DRUG', 'LEVOTHYROXINE', 'IRON', 'TIMING', '갑상선약과 철분은 시간 간격을 두고 드세요', FALSE, 'S-16', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-17b', 'DRUG', 'LEVOTHYROXINE', 'CALCIUM', 'TIMING', '갑상선약과 칼슘은 시간 간격을 두고 드세요', FALSE, 'S-17', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-18', 'DRUG', 'TETRA_QUINOLONE', 'MAGNESIUM', 'TIMING', '일부 항생제와 마그네슘은 시간 간격을 두고 드세요', FALSE, 'S-13', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-19', 'DRUG', 'SSRI', 'ST_JOHNS_WORT', 'EXCLUDE', '항우울제(SSRI)와 세인트존스워트는 함께 드시면 안 돼요', FALSE, 'S-18', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-21', 'INGREDIENT', 'IRON', 'CALCIUM', 'TIMING', '철분과 칼슘은 다른 시간대에 드세요', FALSE, 'S-16', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-22', 'INGREDIENT', 'IRON', 'ZINC', 'WARN', '고용량 철분은 아연 흡수를 줄일 수 있어요', FALSE, 'S-16', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-23', 'STATUS', 'ADULT_MALE_TAKING_IRON', 'IRON', 'ASK', '철분제를 드시는 이유(처방 여부)를 확인해 주세요', FALSE, 'S-16', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-G1', 'STATUS', 'GUEST', 'POTASSIUM', 'EXCLUDE', '복용 중인 약에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요', TRUE, 'S-14', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-G2', 'STATUS', 'GUEST', 'ST_JOHNS_WORT', 'EXCLUDE', '복용 중인 약에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요', TRUE, 'S-18', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-G3', 'STATUS', 'GUEST', 'VITAMIN_A', 'EXCLUDE', '임신·수유 여부에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요', TRUE, 'S-06', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-G4', 'STATUS', 'GUEST', 'BETA_CAROTENE', 'EXCLUDE', '흡연 여부에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요', TRUE, 'S-05, S-06', TRUE);
-- 약 계열만 아는 경우 (STEP 12). 하위 계열 규칙의 대상 성분을 합쳐 추천은 하되 경고. 항우울제만 위험이 커서 EXCLUDE 유지
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U01', 'DRUG', 'ANTIHYPERTENSIVE_UNKNOWN', 'POTASSIUM', 'WARN', '혈압약 종류에 따라 칼륨 보충이 위험할 수 있어요. 드시기 전에 의사·약사와 상담하세요', FALSE, 'S-14', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U01b', 'DRUG', 'ANTIHYPERTENSIVE_UNKNOWN', 'MAGNESIUM', 'WARN', '일부 혈압약(이뇨제)은 마그네슘을 줄일 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-13', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U02', 'DRUG', 'DIURETIC_UNKNOWN', 'POTASSIUM', 'WARN', '이뇨제 종류에 따라 칼륨 보충이 위험할 수 있어요. 드시기 전에 의사·약사와 상담하세요', FALSE, 'S-14', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U02b', 'DRUG', 'DIURETIC_UNKNOWN', 'MAGNESIUM', 'WARN', '일부 이뇨제는 마그네슘을 줄일 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-13', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U03', 'DRUG', 'ANTIDIABETIC_UNKNOWN', 'VITAMIN_B12', 'WARN', '일부 당뇨약(메트포르민)은 오래 드시면 B12 흡수가 줄 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-12', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U04', 'DRUG', 'ACID_REDUCER_UNKNOWN', 'VITAMIN_B12', 'WARN', '위산억제제를 오래 드시면 B12 흡수가 줄 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-12', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U04b', 'DRUG', 'ACID_REDUCER_UNKNOWN', 'MAGNESIUM', 'WARN', '위산억제제를 오래 드시면 마그네슘이 줄 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-13', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U05', 'DRUG', 'ANTICOAGULANT_UNKNOWN', 'VITAMIN_K', 'WARN', '항응고제 종류에 따라 비타민 K가 약효에 영향을 줄 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-15', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U06', 'DRUG', 'THYROID_MED_UNKNOWN', 'IRON', 'TIMING', '갑상선약과 철분은 시간 간격을 두고 드세요. 간격은 의사·약사와 상담하세요', FALSE, 'S-16', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U06b', 'DRUG', 'THYROID_MED_UNKNOWN', 'CALCIUM', 'TIMING', '갑상선약과 칼슘은 시간 간격을 두고 드세요. 간격은 의사·약사와 상담하세요', FALSE, 'S-17', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U07', 'DRUG', 'ANTIBIOTIC_UNKNOWN', 'MAGNESIUM', 'TIMING', '일부 항생제는 마그네슘과 시간 간격을 두고 드셔야 해요. 의사·약사와 상담하세요', FALSE, 'S-13', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U08', 'DRUG', 'ANTIDEPRESSANT_UNKNOWN', 'ST_JOHNS_WORT', 'EXCLUDE', '항우울제와 세인트존스워트는 함께 드시면 안 돼요. 의사·약사와 상담하세요', FALSE, 'S-18', TRUE);
-- 진단 질환 (STEP 15). 출처를 확인한 성분만, 기본 WARN (추천하되 상담 안내)
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-K01', 'DISEASE', 'KIDNEY', 'POTASSIUM', 'WARN', '신장 질환이 있으면 칼륨 보충제는 먼저 의사·약사와 상담하세요', FALSE, 'S-19', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-K02', 'DISEASE', 'KIDNEY', 'MAGNESIUM', 'WARN', '신장 기능이 떨어져 있으면 마그네슘이 몸에 쌓일 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-20', TRUE);
-- 성분 확장 (STEP 16). 기본 WARN, 출처에 "피할 것"이 있거나 애매하면 EXCLUDE. 이름 아는 약 + 계열 모름(R-U*) 둘 다
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-30', 'DRUG', 'WARFARIN', 'OMEGA3', 'WARN', '와파린과 고용량 오메가3를 함께 드시면 출혈 위험이 높아질 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-21, S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U30', 'DRUG', 'ANTICOAGULANT_UNKNOWN', 'OMEGA3', 'WARN', '항응고제와 오메가3를 함께 드시면 출혈 위험이 높아질 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-21, S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-30b', 'DRUG', 'ANTIPLATELET', 'OMEGA3', 'WARN', '항혈소판제를 드시면 오메가3는 먼저 의사·약사와 상담하세요', FALSE, 'S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U30c', 'DRUG', 'ANTIHYPERTENSIVE_UNKNOWN', 'OMEGA3', 'WARN', '혈압약을 드시면 오메가3는 먼저 의사·약사와 상담하세요', FALSE, 'S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-30c1', 'DRUG', 'ACE_INHIBITOR', 'OMEGA3', 'WARN', '혈압약을 드시면 오메가3는 먼저 의사·약사와 상담하세요', FALSE, 'S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-30c2', 'DRUG', 'ARB', 'OMEGA3', 'WARN', '혈압약을 드시면 오메가3는 먼저 의사·약사와 상담하세요', FALSE, 'S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-30c3', 'DRUG', 'CALCIUM_CHANNEL_BLOCKER', 'OMEGA3', 'WARN', '혈압약을 드시면 오메가3는 먼저 의사·약사와 상담하세요', FALSE, 'S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-30c4', 'DRUG', 'THIAZIDE_LOOP_DIURETIC', 'OMEGA3', 'WARN', '혈압약을 드시면 오메가3는 먼저 의사·약사와 상담하세요', FALSE, 'S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-31', 'DRUG', 'IMMUNOSUPPRESSANT', 'PROBIOTICS', 'EXCLUDE', '면역을 억제하는 약을 드시면 유산균은 피하고 의사·약사와 상담하세요', FALSE, 'S-22', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-32', 'STATUS', 'PREGNANT_OR_LACTATING', 'CURCUMIN', 'EXCLUDE', '임신·수유 중에는 강황·커큐민 보충제를 피하세요', FALSE, 'S-23, S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-33', 'DRUG', 'WARFARIN', 'COQ10', 'WARN', '와파린을 드시면 코엔자임 Q10이 약효에 영향을 줄 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-24, S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U33', 'DRUG', 'ANTICOAGULANT_UNKNOWN', 'COQ10', 'WARN', '항응고제를 드시면 코엔자임 Q10은 먼저 의사·약사와 상담하세요', FALSE, 'S-24, S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-33b', 'DRUG', 'INSULIN', 'COQ10', 'WARN', '인슐린을 쓰시면 코엔자임 Q10은 먼저 의사·약사와 상담하세요', FALSE, 'S-24', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U33b', 'DRUG', 'ANTIDIABETIC_UNKNOWN', 'COQ10', 'WARN', '당뇨약 종류에 따라 코엔자임 Q10과 상호작용할 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-24', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-33c', 'DRUG', 'CHEMOTHERAPY', 'COQ10', 'WARN', '항암 치료 중에는 코엔자임 Q10을 먼저 의사와 상담하세요', FALSE, 'S-24', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-33d', 'STATUS', 'PREGNANT_OR_LACTATING', 'COQ10', 'EXCLUDE', '임신·수유 중에는 코엔자임 Q10을 피하세요', FALSE, 'S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-34', 'DISEASE', 'KIDNEY', 'COLLAGEN', 'WARN', '신장 질환이 있으면 콜라겐은 먼저 의사·약사와 상담하세요', FALSE, 'S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-34b', 'STATUS', 'PREGNANT_OR_LACTATING', 'COLLAGEN', 'EXCLUDE', '임신·수유 중에는 콜라겐 보충제를 피하세요', FALSE, 'S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-35', 'DRUG', 'WARFARIN', 'GLUCOSAMINE', 'WARN', '와파린과 글루코사민을 함께 드시면 출혈 위험이 높아질 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-25', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U35', 'DRUG', 'ANTICOAGULANT_UNKNOWN', 'GLUCOSAMINE', 'WARN', '항응고제와 글루코사민을 함께 드시면 출혈 위험이 높아질 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-25', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-35b', 'DISEASE', 'DIABETES', 'GLUCOSAMINE', 'WARN', '글루코사민은 일부 사람에게서 혈당을 올릴 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-25', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-35c', 'STATUS', 'PREGNANT_OR_LACTATING', 'GLUCOSAMINE', 'EXCLUDE', '임신·수유 중에는 글루코사민을 피하세요', FALSE, 'S-25, S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-36', 'DRUG', 'STATIN', 'NIACIN', 'WARN', '스타틴을 드시면 고용량 나이아신은 권장되지 않아요. 의사·약사와 상담하세요', FALSE, 'S-26', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-36b', 'DRUG', 'METFORMIN', 'NIACIN', 'WARN', '고용량 나이아신은 혈당을 올려 당뇨약 효과를 방해할 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-26, S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U36b', 'DRUG', 'ANTIDIABETIC_UNKNOWN', 'NIACIN', 'WARN', '고용량 나이아신은 혈당을 올려 당뇨약 효과를 방해할 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-26, S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-36c', 'DISEASE', 'DIABETES', 'NIACIN', 'WARN', '고용량 나이아신은 혈당을 올릴 수 있어요. 의사·약사와 상담하세요', FALSE, 'S-26', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-36d', 'DRUG', 'WARFARIN', 'NIACIN', 'WARN', '항응고제를 드시면 나이아신은 먼저 의사·약사와 상담하세요', FALSE, 'S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-U36d', 'DRUG', 'ANTICOAGULANT_UNKNOWN', 'NIACIN', 'WARN', '항응고제를 드시면 나이아신은 먼저 의사·약사와 상담하세요', FALSE, 'S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-36e', 'STATUS', 'PREGNANT_OR_LACTATING', 'NIACIN', 'EXCLUDE', '임신·수유 중에는 나이아신 보충제를 피하세요', FALSE, 'S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-37', 'STATUS', 'PREGNANT_OR_LACTATING', 'MILK_THISTLE', 'EXCLUDE', '임신·수유 중에는 밀크씨슬을 피하세요', FALSE, 'S-27, S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-38', 'STATUS', 'PREGNANT_OR_LACTATING', 'LUTEIN', 'EXCLUDE', '임신·수유 중에는 루테인 보충제를 피하세요', FALSE, 'S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-39', 'DRUG', 'ANTICONVULSANT', 'VITAMIN_B6', 'WARN', '뇌전증 약을 드시면 비타민 B6는 먼저 의사·약사와 상담하세요', FALSE, 'S-28', TRUE);
-- 성분 확장 비회원 보수 규칙 (STEP 16). 회원 규칙에 EXCLUDE가 있는 성분은 비회원 추천에서 전부 제외 (R-G1~R-G4와 같은 기준)
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-G5', 'STATUS', 'GUEST', 'CURCUMIN', 'EXCLUDE', '임신·수유 여부에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요', TRUE, 'S-23, S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-G6', 'STATUS', 'GUEST', 'COQ10', 'EXCLUDE', '임신·수유 여부에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요', TRUE, 'S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-G7', 'STATUS', 'GUEST', 'COLLAGEN', 'EXCLUDE', '임신·수유 여부에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요', TRUE, 'S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-G8', 'STATUS', 'GUEST', 'GLUCOSAMINE', 'EXCLUDE', '임신·수유 여부에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요', TRUE, 'S-25, S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-G9', 'STATUS', 'GUEST', 'NIACIN', 'EXCLUDE', '임신·수유 여부에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요', TRUE, 'S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-G10', 'STATUS', 'GUEST', 'MILK_THISTLE', 'EXCLUDE', '임신·수유 여부에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요', TRUE, 'S-27, S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-G11', 'STATUS', 'GUEST', 'LUTEIN', 'EXCLUDE', '임신·수유 여부에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요', TRUE, 'S-30', TRUE);
INSERT IGNORE INTO recommendation_rules (rule_id, condition_type, condition_code, target_ingredient, action, message_ko, guest_excluded, source_id, active) VALUES ('R-G12', 'STATUS', 'GUEST', 'PROBIOTICS', 'EXCLUDE', '복용 중인 약에 따라 위험할 수 있어 회원 맞춤 추천에서 확인해 드려요', TRUE, 'S-22', TRUE);
