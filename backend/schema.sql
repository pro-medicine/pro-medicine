-- 약쏙해줘 스키마 v3 + 예시 데이터 / MySQL 8.0
-- ============================================================
-- 기존 테이블 삭제 (FK 의존 순서: 자식 -> 부모)
-- 주의: 실행하면 기존 데이터가 모두 삭제됩니다.
-- ============================================================
DROP TABLE IF EXISTS `llm_call_logs`;
DROP TABLE IF EXISTS `ingredient_standards`;
DROP TABLE IF EXISTS `recommendation_rules`;
DROP TABLE IF EXISTS `survey_answers`;
DROP TABLE IF EXISTS `question_options`;
DROP TABLE IF EXISTS `survey_questions`;
DROP TABLE IF EXISTS `ai_recommendation_items`;
DROP TABLE IF EXISTS `ai_recommendations`;
DROP TABLE IF EXISTS `ai_question_turns`;
DROP TABLE IF EXISTS `ai_question_sessions`;
DROP TABLE IF EXISTS `survey_responses`;
DROP TABLE IF EXISTS `surveys`;
DROP TABLE IF EXISTS `user_medications`;
DROP TABLE IF EXISTS `drugs`;
DROP TABLE IF EXISTS `health_profiles`;
DROP TABLE IF EXISTS `user_conditions`;
DROP TABLE IF EXISTS `notifications`;
DROP TABLE IF EXISTS `intake_records`;
DROP TABLE IF EXISTS `intake_schedules`;
DROP TABLE IF EXISTS `user_products`;
DROP TABLE IF EXISTS `review_product_scores`;
DROP TABLE IF EXISTS `review_mentions`;
DROP TABLE IF EXISTS `product_ingredients`;
DROP TABLE IF EXISTS `catalog_products`;
DROP TABLE IF EXISTS `guardian_settings`;
DROP TABLE IF EXISTS `follows`;
DROP TABLE IF EXISTS `disposal_locations`;
DROP TABLE IF EXISTS `calendar_events`;
DROP TABLE IF EXISTS `users`;

-- ============================================================
-- 테이블 생성
-- ============================================================

CREATE TABLE `health_profiles` (
                                   `user_id`   BIGINT   NOT NULL,
                                   `birth_year`   SMALLINT   NULL,
                                   `gender`   VARCHAR(10)   NULL,
                                   `height`   DECIMAL(5,1)   NULL,
                                   `weight`   DECIMAL(5,1)   NULL,
                                   `smoking`   VARCHAR(20)   NULL,
                                   `drinking`   VARCHAR(20)   NULL,
                                   `caffeine`   VARCHAR(20)   NULL,
                                   `diet_type`   VARCHAR(20)   NULL,
                                   `sun_exposure`   VARCHAR(20)   NULL,
                                   `updated_at`   DATETIME   NOT NULL   DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `user_conditions` (
                                   `user_condition_id`   BIGINT   NOT NULL,
                                   `user_id`   BIGINT   NOT NULL,
                                   `condition_name`   VARCHAR(70)   NOT NULL,
                                   `created_at`   DATETIME   NOT NULL   DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `intake_records` (
                                  `intake_record_id`   BIGINT   NOT NULL,
                                  `schedule_id`   BIGINT   NOT NULL,
                                  `intake_date`   DATE   NOT NULL,
                                  `status`   VARCHAR(10)   NOT NULL,
                                  `completed_at`   DATETIME   NULL,
                                  `proof_image_url`   VARCHAR(1000)   NULL,
                                  `is_shared`   BOOLEAN   NOT NULL   DEFAULT FALSE
);

CREATE TABLE `users` (
                         `user_id`   BIGINT   NOT NULL,
                         `email`   VARCHAR(255)   NOT NULL,
                         `password_hash`   VARCHAR(255)   NOT NULL,
                         `nickname`   VARCHAR(50)   NOT NULL,
                         `share_intake_status`   BOOLEAN   NOT NULL   DEFAULT FALSE,
                         `sensitive_consent`   BOOLEAN   NOT NULL   DEFAULT FALSE,
                         `sensitive_consent_at`   DATETIME   NULL,
                         `created_at`   DATETIME   NOT NULL   DEFAULT CURRENT_TIMESTAMP,
                         `updated_at`   DATETIME   NOT NULL   DEFAULT CURRENT_TIMESTAMP,
                         `last_active_at` DATETIME   NOT NULL   DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `user_products` (
                                 `user_product_id`   BIGINT   NOT NULL,
                                 `user_id`   BIGINT   NOT NULL,
                                 `catalog_product_id`   BIGINT   NULL,
                                 `product_kind`   VARCHAR(15)   NOT NULL,
                                 `manual_product_name`   VARCHAR(255)   NULL,
                                 `registered_via`   VARCHAR(15)   NOT NULL   DEFAULT 'SEARCH',
                                 `start_date`   DATE   NULL,
                                 `is_active`   BOOLEAN   NOT NULL   DEFAULT TRUE,
                                 `created_at`   DATETIME   NOT NULL   DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `catalog_products` (
                                    `catalog_product_id`   BIGINT   NOT NULL,
                                    `mfds_item_no`   VARCHAR(100)   NULL,
                                    `product_name`   VARCHAR(255)   NOT NULL,
                                    `manufacturer`   VARCHAR(255)   NULL,
                                    `main_function`   TEXT   NULL,
                                    `intake_method`   TEXT   NULL,
                                    `intake_caution`   TEXT   NULL,
                                    `image_url`   VARCHAR(1000)   NULL,
                                    `category`   VARCHAR(50)   NULL,
                                    `ingredient_summary`   VARCHAR(255)   NULL,
                                    `package_size`   VARCHAR(100)   NULL,
                                    `price`   INT   NULL,
                                    `created_at`   DATETIME   NOT NULL   DEFAULT CURRENT_TIMESTAMP,
    -- STEP 17: 리뷰 제품 추천 (V3). 주연님 검색·상세는 이 컬럼을 쓰지 않는다
                                    `name_ko`   VARCHAR(255)   NULL   COMMENT '화면 표시용 한글 이름. 없으면 product_name',
                                    `brand`   VARCHAR(255)   NULL,
                                    `data_source`   VARCHAR(20)   NOT NULL   DEFAULT 'SAMPLE'   COMMENT 'DSLD, MFDS, MANUAL, SAMPLE',
                                    `external_id`   VARCHAR(64)   NULL   COMMENT 'DSLD 라벨 ID / 품목제조신고번호',
                                    `is_active`   BOOLEAN   NOT NULL   DEFAULT TRUE   COMMENT '단종·비노출이면 FALSE',
                                    `blocked_kr`   BOOLEAN   NOT NULL   DEFAULT FALSE   COMMENT '국내 반입 차단 성분(멜라토닌 등) 포함',
                                    `image_key`   VARCHAR(500)   NULL   COMMENT 'S3 키 (리팩토링 때 사용)',
                                    `updated_at`   DATETIME   NULL   COMMENT '리뷰 데이터 import 시각',
                                    UNIQUE KEY `uk_catalog_products_source_external` (`data_source`, `external_id`)
);

-- 리뷰 제품 추천 (STEP 17). 서버는 읽기만 하고 월 1회 SQL import로 채운다. ingredient_code = IngredientCode 24개 + OTHER
CREATE TABLE `product_ingredients` (
                                       `product_ingredient_id`   BIGINT   NOT NULL AUTO_INCREMENT,
                                       `catalog_product_id`   BIGINT   NOT NULL,
                                       `ingredient_code`   VARCHAR(30)   NOT NULL   COMMENT 'IngredientCode 24개 또는 OTHER',
                                       `ingredient_name`   VARCHAR(100)   NOT NULL   COMMENT '라벨 원래 이름',
                                       `amount_per_day`   DECIMAL(12,3)   NULL   COMMENT 'DSLD만. NULL = 들어 있음',
                                       `unit`   VARCHAR(20)   NULL,
                                       `source`   VARCHAR(20)   NULL   COMMENT 'DSLD_LABEL, MFDS_FUNCTIONAL, MANUAL',
                                       PRIMARY KEY (`product_ingredient_id`),
                                       UNIQUE KEY `uk_product_ingredients` (`catalog_product_id`, `ingredient_code`, `ingredient_name`)
);

CREATE TABLE `review_mentions` (
                                   `mention_id`   BIGINT   NOT NULL AUTO_INCREMENT,
                                   `video_id`   VARCHAR(20)   NOT NULL,
                                   `mention_no`   INT   NOT NULL,
                                   `channel_id`   VARCHAR(30)   NOT NULL   COMMENT '약사 채널 (점수 계산용, 화면 노출 안 함)',
                                   `published_at`   DATE   NULL,
                                   `catalog_product_id`   BIGINT   NULL   COMMENT '제품 확정 못 하면 NULL',
                                   `resolve_status`   VARCHAR(20)   NOT NULL   COMMENT 'RESOLVED, PENDING_REVIEW',
                                   `product_name_ko`   VARCHAR(255)   NULL,
                                   `brand_en`   VARCHAR(255)   NULL,
                                   `ingredients`   JSON   NULL   COMMENT '["MAGNESIUM"] 어떤 성분 제품으로 말했나',
                                   `stance`   VARCHAR(15)   NOT NULL   COMMENT 'RECOMMEND, NOT_RECOMMEND, NEUTRAL',
                                   `is_sponsored`   BOOLEAN   NOT NULL   DEFAULT FALSE,
                                   `purpose_tags`   JSON   NULL   COMMENT 'V4용',
                                   `cautions`   JSON   NULL   COMMENT '[{category, text}]',
                                   `context`   VARCHAR(400)   NULL   COMMENT 'V4 검색용, API로 내보내지 않음',
                                   `embedding`   MEDIUMBLOB   NULL   COMMENT 'context 벡터 float32 x 1536 (V4용)',
                                   `embedding_model`   VARCHAR(80)   NULL,
                                   `start_sec`   INT   NULL,
                                   `evidence_quote`   TEXT   NULL   COMMENT '검수용, API로 내보내지 않음',
                                   `prompt_version`   VARCHAR(30)   NULL,
                                   PRIMARY KEY (`mention_id`),
                                   UNIQUE KEY `uk_review_mentions_video` (`video_id`, `mention_no`),
                                   KEY `idx_review_mentions_product` (`catalog_product_id`)
);

CREATE TABLE `review_product_scores` (
                                         `ingredient_code`   VARCHAR(30)   NOT NULL,
                                         `catalog_product_id`   BIGINT   NOT NULL,
                                         `base_score`   DECIMAL(8,3)   NOT NULL,
                                         `recommend_channels`   INT   NOT NULL   DEFAULT 0,
                                         `not_recommend_channels`   INT   NOT NULL   DEFAULT 0,
                                         `last_published_at`   DATE   NULL,
                                         `common_cautions`   JSON   NULL   COMMENT '2명 이상 약사가 말한 주의점',
                                         `mention_ids`   JSON   NULL,
                                         `computed_at`   DATETIME   NULL,
                                         PRIMARY KEY (`ingredient_code`, `catalog_product_id`),
                                         KEY `idx_review_product_scores_rank` (`ingredient_code`, `base_score` DESC),
                                         KEY `idx_review_product_scores_product` (`catalog_product_id`)
);

CREATE TABLE `notifications` (
                                 `notification_id`   BIGINT   NOT NULL AUTO_INCREMENT,
                                 `user_id`   BIGINT   NOT NULL,
                                 `schedule_id`   BIGINT   NULL,
                                 `actor_user_id`   BIGINT   NULL,
                                 `notification_type`   VARCHAR(30)   NOT NULL,
                                 `title`   VARCHAR(150)   NOT NULL,
                                 `scheduled_at`   DATETIME   NULL,
                                 `sent_at`   DATETIME   NULL,
                                 `read_at`   DATETIME   NULL,
                                 `created_at`   DATETIME   NOT NULL   DEFAULT CURRENT_TIMESTAMP,
                                 `dedup_key`   VARCHAR(150)   NOT NULL,
                                 PRIMARY KEY (`notification_id`),
                                 UNIQUE KEY `uq_notifications_dedup_key` (`dedup_key`),
                                 KEY `idx_notifications_inbox` (`user_id`, `read_at`, `created_at`),
                                 CONSTRAINT `ck_notif_kind` CHECK (`notification_type` IN ('INTAKE_REMINDER','FRIEND_GOAL_ACHIEVED','NEW_FOLLOWER', 'FOLLOW_ACCEPTED', 'GUARDIAN'))
);

CREATE TABLE `guardian_settings` (
                                     `guardian_setting_id`   BIGINT   NOT NULL AUTO_INCREMENT,
                                     `user_id`   BIGINT   NOT NULL,
                                     `guardian_user_id`   BIGINT   NOT NULL,
                                     `inactivity_days`   INT   NOT NULL   DEFAULT 3,
                                     `consented_at`   DATETIME   NULL,
                                     `is_active`   BOOLEAN   NOT NULL   DEFAULT FALSE,
                                     `last_notified_at`   DATETIME   NULL,
                                     PRIMARY KEY (`guardian_setting_id`),
                                     UNIQUE KEY `uq_guardian_pair` (`user_id`, `guardian_user_id`)
);

CREATE TABLE `follows` (
                           `follow_id`   BIGINT   NOT NULL AUTO_INCREMENT,
                           `follower_id`   BIGINT   NOT NULL,
                           `following_id`   BIGINT   NOT NULL,
                           `status`   VARCHAR(10)   NOT NULL   DEFAULT 'PENDING',
                           `created_at`   DATETIME   NOT NULL   DEFAULT CURRENT_TIMESTAMP,
                           PRIMARY KEY (`follow_id`)
);

CREATE TABLE `disposal_locations` (
                                      `disposal_location_id`   BIGINT   NOT NULL,
                                      `location_name`   VARCHAR(200)   NOT NULL,
                                      `location_type`   VARCHAR(20)   NOT NULL,
                                      `address`   VARCHAR(500)   NOT NULL,
                                      `latitude`   DECIMAL(10,7)   NOT NULL,
                                      `longitude`   DECIMAL(10,7)   NOT NULL,
                                      `source_name`   VARCHAR(100)   NULL,
                                      `source_updated_at`   DATETIME   NULL
);

CREATE TABLE `calendar_events` (
                                  `event_id` BIGINT NOT NULL AUTO_INCREMENT,
                                  `user_id` BIGINT NOT NULL,
                                  `event_type` VARCHAR(30) NOT NULL,
                                  `title` VARCHAR(150) NOT NULL,
                                  `scheduled_at` DATETIME NOT NULL,
                                  `notes` VARCHAR(1000) NULL,
                                  `status` VARCHAR(20) NOT NULL DEFAULT 'PLANNED',
                                  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                  PRIMARY KEY (`event_id`),

                                  CONSTRAINT `chk_calendar_event_type`
                                      CHECK (`event_type` IN (
                                                              'SUPPLEMENT',
                                                              'INJECTION',
                                                              'MEDICATION',
                                                              'ONE_TIME_INTAKE'
                                                              'APPOINTMENT',
                                                              'OTHER'
                                          )),

                                  CONSTRAINT `chk_calendar_event_status`
                                      CHECK (`status` IN (
                                                          'PLANNED',
                                                          'COMPLETED',
                                                          'CANCELED'
                                          ))
);

CREATE TABLE `ai_recommendation_items` (
                                           `recommendation_item_id`   BIGINT   NOT NULL,
                                           `recommendation_id`   BIGINT   NOT NULL,
                                           `ingredient_name`   VARCHAR(150)   NOT NULL,
                                           `reason`   TEXT   NULL,
                                           `sort_order`   INT   NOT NULL   DEFAULT 1,
                                           `recommended_amount`   DECIMAL(10,2)   NULL,
                                           `current_intake`   DECIMAL(10,2)   NULL,
                                           `intake_status`   VARCHAR(10)   NULL   COMMENT 'KNOWN, UNKNOWN',
                                           `unit`   VARCHAR(10)   NULL,
                                           `dose_note`   VARCHAR(200)   NULL   COMMENT '상한 조정, 기준값 미등록 등',
                                           `excluded`   BOOLEAN   NOT NULL   DEFAULT FALSE,
                                           `applied_rule_ids`   VARCHAR(200)   NULL   COMMENT '쉼표 구분',
                                           `warnings`   TEXT   NULL   COMMENT 'JSON 배열'
);

CREATE TABLE `ai_recommendations` (
                                      `recommendation_id`   BIGINT   NOT NULL,
                                      `response_id`   BIGINT   NULL   COMMENT 'V1 설문 응답. V2 정밀 추천은 NULL (STEP 14)',
                                      `session_id`   BIGINT   NULL   COMMENT 'V2 맞춤 질문 회차 (STEP 14, 90일 삭제 후 NULL)',
                                      `summary`   TEXT   NULL,
                                      `model_name`   VARCHAR(60)   NULL,
                                      `created_at`   DATETIME   NOT NULL   DEFAULT CURRENT_TIMESTAMP,
                                      `status`   VARCHAR(10)   NOT NULL   DEFAULT 'DONE'   COMMENT 'PENDING, PROCESSING, DONE, FAILED',
                                      `started_at`   DATETIME   NULL,
                                      `finished_at`   DATETIME   NULL,
                                      `error_code`   VARCHAR(30)   NULL
);

-- AI 추천: LLM 호출 기록 (STEP 03). 프롬프트·응답 본문은 저장하지 않는다 (민감정보).
CREATE TABLE `llm_call_logs` (
                                 `llm_call_log_id`   BIGINT   NOT NULL AUTO_INCREMENT,
                                 `response_id`   BIGINT   NULL   COMMENT 'survey_responses 참조 (평가 실행 중이면 null, FK 없음)',
                                 `eval_run_id`   VARCHAR(50)   NULL,
                                 `session_id`   BIGINT   NULL   COMMENT 'ai_question_sessions 참조 (V2 호출만, FK 없음, STEP 14)',
                                 `pipeline_version`   VARCHAR(10)   NOT NULL,
                                 `call_tag`   VARCHAR(30)   NOT NULL   COMMENT 'RECOMMEND, RECOMMEND_REPAIR, PQ_QUESTION, PQ_QUESTION_REPAIR, PQ_QUESTION_BATCH, PQ_QUESTION_FOLLOWUP_BATCH, PQ_QUESTION_HEDGE',
                                 `model`   VARCHAR(60)   NOT NULL,
                                 `prompt_version`   VARCHAR(20)   NOT NULL,
                                 `input_tokens`   INT   NULL,
                                 `output_tokens`   INT   NULL,
                                 `cache_read_tokens`   INT   NULL,
                                 `cache_creation_tokens`   INT   NULL,
                                 `latency_ms`   INT   NOT NULL,
                                 `attempt_no`   INT   NOT NULL   DEFAULT 1   COMMENT '네트워크 재시도(STEP 10)만 증가',
                                 `outcome`   VARCHAR(20)   NOT NULL   COMMENT 'SUCCESS, PARSE_FAIL, VALIDATION_FAIL, TIMEOUT, HTTP_ERROR',
                                 `http_status`   INT   NULL,
                                 `created_at`   DATETIME   NOT NULL   DEFAULT CURRENT_TIMESTAMP,
                                 PRIMARY KEY (`llm_call_log_id`),
                                 KEY `idx_llm_call_logs_eval_run_id` (`eval_run_id`)
);

-- AI 추천: 성분 기준값 (STEP 06). 시드는 docs/sql/STEP06_seed_ingredient_standards.sql
CREATE TABLE `ingredient_standards` (
                                        `standard_id`   BIGINT   NOT NULL AUTO_INCREMENT,
                                        `ingredient_code`   VARCHAR(30)   NOT NULL,
                                        `gender`   VARCHAR(5)   NOT NULL   COMMENT 'M, F, ALL',
                                        `age_min`   INT   NOT NULL,
                                        `age_max`   INT   NULL   COMMENT '상한 없으면 NULL',
                                        `condition_code`   VARCHAR(30)   NULL   COMMENT 'SMOKING_CURRENT 등 (STEP 07 규칙과 같은 코드)',
                                        `recommended_amount`   DECIMAL(10,2)   NULL,
                                        `upper_limit`   DECIMAL(10,2)   NULL,
                                        `unit`   VARCHAR(10)   NOT NULL,
                                        `standard_version`   VARCHAR(20)   NOT NULL   COMMENT 'KDRI-2025, NIH-ODS',
                                        `source_id`   VARCHAR(10)   NOT NULL   COMMENT 'domain-rules.md의 S-xx',
                                        PRIMARY KEY (`standard_id`)
);

-- AI 추천: 안전 규칙 (STEP 07). 시드는 docs/sql/STEP07_seed_recommendation_rules.sql
CREATE TABLE `recommendation_rules` (
                                        `rule_id`   VARCHAR(10)   NOT NULL,
                                        `condition_type`   VARCHAR(15)   NOT NULL   COMMENT 'HABIT, DRUG, STATUS, INGREDIENT, DISEASE',
                                        `condition_code`   VARCHAR(40)   NOT NULL,
                                        `target_ingredient`   VARCHAR(30)   NOT NULL,
                                        `action`   VARCHAR(10)   NOT NULL   COMMENT 'EXCLUDE, WARN, TIMING, ADJUST, ASK',
                                        `message_ko`   VARCHAR(300)   NOT NULL,
                                        `guest_excluded`   BOOLEAN   NOT NULL   DEFAULT FALSE,
                                        `source_id`   VARCHAR(20)   NOT NULL,
                                        `active`   BOOLEAN   NOT NULL   DEFAULT TRUE,
                                        PRIMARY KEY (`rule_id`)
);

-- AI 추천 V2: 회원 맞춤 질문 회차·질문 (STEP 14). 맞춤 답변은 프로필에 저장하지 않고 여기에만 남긴다 (90일 뒤 삭제)
CREATE TABLE `ai_question_sessions` (
                                        `session_id`   BIGINT   NOT NULL AUTO_INCREMENT,
                                        `user_id`   BIGINT   NOT NULL,
                                        `status`   VARCHAR(15)   NOT NULL   COMMENT 'IN_PROGRESS, READY, DONE, ABANDONED, FAILED',
                                        `pregnancy`   VARCHAR(20)   NULL   COMMENT 'NOT_APPLICABLE, POSSIBLE, PREGNANT, LACTATING (19~50세 여성만)',
                                        `question_count`   INT   NOT NULL   DEFAULT 0   COMMENT 'LLM 질문 수 (고정 질문 제외)',
                                        `stopped_by_user`   BOOLEAN   NOT NULL   DEFAULT FALSE,
                                        `recommendation_id`   BIGINT   NULL,
                                        `baseline_recommendation_id`   BIGINT   NULL   COMMENT '모니터링용: 비교한 V1 추천',
                                        `diff_json`   JSON   NULL   COMMENT '모니터링용: V1 대비 추가·제외·순서 변화',
                                        `error_code`   VARCHAR(20)   NULL,
                                        `started_at`   DATETIME   NOT NULL   DEFAULT CURRENT_TIMESTAMP,
                                        `finished_at`   DATETIME   NULL,
                                        PRIMARY KEY (`session_id`),
                                        KEY `idx_pq_sessions_user` (`user_id`, `started_at`),
                                        KEY `idx_pq_sessions_started` (`started_at`)
);

CREATE TABLE `ai_question_turns` (
                                     `turn_id`   BIGINT   NOT NULL AUTO_INCREMENT,
                                     `session_id`   BIGINT   NOT NULL,
                                     `turn_no`   INT   NOT NULL   COMMENT '0 = 고정 질문(임신), 1부터 LLM 질문',
                                     `fixed`   BOOLEAN   NOT NULL   DEFAULT FALSE,
                                     `topic`   VARCHAR(60)   NOT NULL,
                                     `depth`   INT   NOT NULL,
                                     `question_text`   VARCHAR(200)   NOT NULL,
                                     `answer_type`   VARCHAR(10)   NOT NULL   COMMENT 'SINGLE, MULTI',
                                     `options_json`   JSON   NOT NULL   COMMENT '[{"label":"4년 이상","kind":"CONCERN"}, ...] 최대 5개',
                                     `selected_json`   JSON   NULL   COMMENT '[2] 또는 [0,3], 답하기 전 NULL',
                                     `llm_call_log_id`   BIGINT   NULL,
                                     `created_at`   DATETIME   NOT NULL   DEFAULT CURRENT_TIMESTAMP,
                                     `answered_at`   DATETIME   NULL,
                                     PRIMARY KEY (`turn_id`),
                                     UNIQUE KEY `uq_pq_turn` (`session_id`, `turn_no`)
);

CREATE TABLE `intake_schedules` (
                                    `schedule_id`   BIGINT   NOT NULL,
                                    `user_product_id`   BIGINT   NOT NULL,
                                    `meal_slot`   VARCHAR(15)   NOT NULL,
                                    `meal_relation`   VARCHAR(10)   NOT NULL,
                                    `remind_at`   TIME   NOT NULL,
                                    `is_enabled`   BOOLEAN   NOT NULL   DEFAULT TRUE,
                                    `created_at`   DATETIME   NOT NULL   DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `surveys` (
                           `survey_id`   BIGINT   NOT NULL,
                           `version`   VARCHAR(20)   NOT NULL,
                           `status`   VARCHAR(10)   NOT NULL   DEFAULT 'DRAFT'
);

CREATE TABLE `survey_questions` (
                                    `question_id`   BIGINT   NOT NULL,
                                    `survey_id`   BIGINT   NOT NULL,
                                    `code`   VARCHAR(20)   NOT NULL,
                                    `question_text`   TEXT   NOT NULL,
                                    `answer_type`   VARCHAR(10)   NOT NULL,
                                    `profile_key`   VARCHAR(50)   NULL,
                                    `is_sensitive`   BOOLEAN   NOT NULL   DEFAULT FALSE,
                                    `guest_visible`   BOOLEAN   NOT NULL   DEFAULT TRUE,
                                    `display_order`   INT   NOT NULL
);

CREATE TABLE `question_options` (
                                    `option_id`   BIGINT   NOT NULL,
                                    `question_id`   BIGINT   NOT NULL,
                                    `value_code`   VARCHAR(50)   NOT NULL,
                                    `label`   VARCHAR(100)   NOT NULL,
                                    `display_order`   INT   NOT NULL
);

CREATE TABLE `survey_responses` (
                                    `response_id`   BIGINT   NOT NULL,
                                    `survey_id`   BIGINT   NOT NULL,
                                    `user_id`   BIGINT   NULL,
                                    `respondent_type`   VARCHAR(10)   NOT NULL,
                                    `includes_sensitive`   BOOLEAN   NOT NULL   DEFAULT FALSE,
                                    `status`   VARCHAR(15)   NOT NULL   DEFAULT 'IN_PROGRESS',
                                    `submitted_at`   DATETIME   NULL
);

CREATE TABLE `survey_answers` (
                                  `answer_id`   BIGINT   NOT NULL,
                                  `response_id`   BIGINT   NOT NULL,
                                  `question_id`   BIGINT   NOT NULL,
                                  `option_id`   BIGINT   NULL,
                                  `value_number`   DECIMAL(10,2)   NULL,
                                  `value_text`   TEXT   NULL
);

CREATE TABLE `drugs` (
                         `drug_id`   VARCHAR(50)   NOT NULL,
                         `name`   VARCHAR(150)   NOT NULL,
                         `drug_class`   VARCHAR(50)   NOT NULL
);

CREATE TABLE `user_medications` (
                                    `user_medication_id`   BIGINT   NOT NULL,
                                    `user_id`   BIGINT   NOT NULL,
                                    `drug_id`   VARCHAR(50)   NULL,
                                    `unmatched_name`   VARCHAR(150)   NULL,
                                    `updated_at`   DATETIME   NOT NULL   DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE `health_profiles` ADD CONSTRAINT `PK_HEALTH_PROFILES` PRIMARY KEY (
                                                                               `user_id`
    );

ALTER TABLE `user_conditions` ADD CONSTRAINT `PK_USER_CONDITIONS` PRIMARY KEY (
                                                                               `user_condition_id`
    );

ALTER TABLE `intake_records` ADD CONSTRAINT `PK_INTAKE_RECORDS` PRIMARY KEY (
                                                                             `intake_record_id`
    );

ALTER TABLE `users` ADD CONSTRAINT `PK_USERS` PRIMARY KEY (
                                                           `user_id`
    );
ALTER TABLE `calendar_events`
    ADD CONSTRAINT `fk_calendar_event_user`
        FOREIGN KEY (`user_id`)
            REFERENCES `users` (`user_id`)
            ON DELETE CASCADE;

ALTER TABLE `user_products` ADD CONSTRAINT `PK_USER_PRODUCTS` PRIMARY KEY (
                                                                           `user_product_id`
    );

ALTER TABLE `catalog_products` ADD CONSTRAINT `PK_CATALOG_PRODUCTS` PRIMARY KEY (
                                                                                 `catalog_product_id`
    );

ALTER TABLE `disposal_locations` ADD CONSTRAINT `PK_DISPOSAL_LOCATIONS` PRIMARY KEY (
                                                                                     `disposal_location_id`
    );

ALTER TABLE `ai_recommendation_items` ADD CONSTRAINT `PK_AI_RECOMMENDATION_ITEMS` PRIMARY KEY (
                                                                                               `recommendation_item_id`
    );

ALTER TABLE `ai_recommendations` ADD CONSTRAINT `PK_AI_RECOMMENDATIONS` PRIMARY KEY (
                                                                                     `recommendation_id`
    );

ALTER TABLE `intake_schedules` ADD CONSTRAINT `PK_INTAKE_SCHEDULES` PRIMARY KEY (
                                                                                 `schedule_id`
    );

ALTER TABLE `surveys` ADD CONSTRAINT `PK_SURVEYS` PRIMARY KEY (
                                                               `survey_id`
    );

ALTER TABLE `survey_questions` ADD CONSTRAINT `PK_SURVEY_QUESTIONS` PRIMARY KEY (
                                                                                 `question_id`
    );

ALTER TABLE `question_options` ADD CONSTRAINT `PK_QUESTION_OPTIONS` PRIMARY KEY (
                                                                                 `option_id`
    );

ALTER TABLE `survey_responses` ADD CONSTRAINT `PK_SURVEY_RESPONSES` PRIMARY KEY (
                                                                                 `response_id`
    );

ALTER TABLE `survey_answers` ADD CONSTRAINT `PK_SURVEY_ANSWERS` PRIMARY KEY (
                                                                             `answer_id`
    );

ALTER TABLE `drugs` ADD CONSTRAINT `PK_DRUGS` PRIMARY KEY (
                                                           `drug_id`
    );

ALTER TABLE `user_medications` ADD CONSTRAINT `PK_USER_MEDICATIONS` PRIMARY KEY (
                                                                                 `user_medication_id`
    );

-- JPA IDENTITY 엔티티(설문·AI 추천·건강 프로필)의 PK 자동 증가. PK 추가 후, FK 추가 전에 붙인다.
ALTER TABLE `surveys` MODIFY `survey_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `survey_questions` MODIFY `question_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `question_options` MODIFY `option_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `survey_responses` MODIFY `response_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `survey_answers` MODIFY `answer_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `user_conditions` MODIFY `user_condition_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `user_medications` MODIFY `user_medication_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `ai_recommendations` MODIFY `recommendation_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `ai_recommendation_items` MODIFY `recommendation_item_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `user_products` MODIFY `user_product_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `intake_schedules` MODIFY `schedule_id` BIGINT NOT NULL AUTO_INCREMENT;
-- STEP 17: CatalogProduct 엔티티도 IDENTITY
ALTER TABLE `catalog_products` MODIFY `catalog_product_id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `health_profiles` ADD CONSTRAINT `FK_users_TO_health_profiles_1` FOREIGN KEY (
                                                                                          `user_id`
    )
    REFERENCES `users` (
                        `user_id`
        );

ALTER TABLE `survey_questions` ADD CONSTRAINT `FK_surveys_TO_survey_questions_1` FOREIGN KEY (
                                                                                              `survey_id`
    )
    REFERENCES `surveys` (
                          `survey_id`
        );

ALTER TABLE `question_options` ADD CONSTRAINT `FK_survey_questions_TO_question_options_1` FOREIGN KEY (
                                                                                                       `question_id`
    )
    REFERENCES `survey_questions` (
                                   `question_id`
        );

ALTER TABLE `survey_responses` ADD CONSTRAINT `FK_surveys_TO_survey_responses_1` FOREIGN KEY (
                                                                                              `survey_id`
    )
    REFERENCES `surveys` (
                          `survey_id`
        );

ALTER TABLE `survey_responses` ADD CONSTRAINT `FK_users_TO_survey_responses_1` FOREIGN KEY (
                                                                                            `user_id`
    )
    REFERENCES `users` (
                        `user_id`
        );

ALTER TABLE `survey_answers` ADD CONSTRAINT `FK_survey_responses_TO_survey_answers_1` FOREIGN KEY (
                                                                                                   `response_id`
    )
    REFERENCES `survey_responses` (
                                   `response_id`
        );

ALTER TABLE `survey_answers` ADD CONSTRAINT `FK_survey_questions_TO_survey_answers_1` FOREIGN KEY (
                                                                                                   `question_id`
    )
    REFERENCES `survey_questions` (
                                   `question_id`
        );

ALTER TABLE `survey_answers` ADD CONSTRAINT `FK_question_options_TO_survey_answers_1` FOREIGN KEY (
                                                                                                   `option_id`
    )
    REFERENCES `question_options` (
                                   `option_id`
        );

ALTER TABLE `user_medications` ADD CONSTRAINT `FK_users_TO_user_medications_1` FOREIGN KEY (
                                                                                            `user_id`
    )
    REFERENCES `users` (
                        `user_id`
        );

ALTER TABLE `user_medications` ADD CONSTRAINT `FK_drugs_TO_user_medications_1` FOREIGN KEY (
                                                                                            `drug_id`
    )
    REFERENCES `drugs` (
                        `drug_id`
        );

ALTER TABLE `ai_recommendations` ADD CONSTRAINT `FK_survey_responses_TO_ai_recommendations_1` FOREIGN KEY (
                                                                                                           `response_id`
    )
    REFERENCES `survey_responses` (
                                   `response_id`
        );

-- STEP 14: 회원이 삭제되면 회차·질문도 삭제. 회차가 90일 뒤 삭제되면 추천 결과는 남기고 session_id만 비운다
-- 엔티티 AiRecommendationItem.recommendation(@ManyToOne)과 같은 FK (2026-10-29 대조 때 빠진 것을 발견해 추가)
ALTER TABLE `ai_recommendation_items` ADD CONSTRAINT `FK_ai_recommendations_TO_ai_recommendation_items_1` FOREIGN KEY (`recommendation_id`)
    REFERENCES `ai_recommendations` (`recommendation_id`);

ALTER TABLE `ai_question_sessions` ADD CONSTRAINT `fk_pq_sessions_user` FOREIGN KEY (`user_id`)
    REFERENCES `users` (`user_id`) ON DELETE CASCADE;

ALTER TABLE `ai_question_turns` ADD CONSTRAINT `fk_pq_turns_session` FOREIGN KEY (`session_id`)
    REFERENCES `ai_question_sessions` (`session_id`) ON DELETE CASCADE;

ALTER TABLE `ai_recommendations` ADD CONSTRAINT `fk_ai_recommendations_session` FOREIGN KEY (`session_id`)
    REFERENCES `ai_question_sessions` (`session_id`) ON DELETE SET NULL;

-- STEP 17: 리뷰 제품 추천. 제품이 삭제되면 성분·점수는 삭제, 언급은 남기고 제품 id만 비운다
ALTER TABLE `product_ingredients` ADD CONSTRAINT `fk_product_ingredients_product` FOREIGN KEY (`catalog_product_id`)
    REFERENCES `catalog_products` (`catalog_product_id`) ON DELETE CASCADE;

ALTER TABLE `review_mentions` ADD CONSTRAINT `fk_review_mentions_product` FOREIGN KEY (`catalog_product_id`)
    REFERENCES `catalog_products` (`catalog_product_id`) ON DELETE SET NULL;

ALTER TABLE `review_product_scores` ADD CONSTRAINT `fk_review_product_scores_product` FOREIGN KEY (`catalog_product_id`)
    REFERENCES `catalog_products` (`catalog_product_id`) ON DELETE CASCADE;

-- ============================================================
-- 예시 데이터 (개발/시연용)
--  * 모든 계정의 비밀번호: password1234!  (BCrypt 해시 저장)
--  * 기준일: 2026-10-21 (오늘 김민지는 3개 중 1개 복용 -> 33%)
--  * 제품명/제조사/수거 위치/좌표는 전부 가상의 샘플입니다.
--  * PK를 직접 지정합니다. (AUTO_INCREMENT 테이블도 다음 값은 지정한 최대 PK 다음부터 이어집니다)
-- ============================================================

-- 사용자 (5명) ------------------------------------------------
INSERT INTO `users` (`user_id`, `email`, `password_hash`, `nickname`, `share_intake_status`, `sensitive_consent`, `sensitive_consent_at`, `created_at`, `updated_at`) VALUES
                                                                                                                                                                          (1, 'minji@example.com',  '$2a$10$8dVc84v3JUy3B042vtUO6uU4n02w3ksd7X0tuY9fJU0JwRseHXJXm', '김민지', TRUE,  TRUE,  '2026-10-01 09:00:00', '2026-10-01 09:00:00', '2026-10-01 09:00:00'),
                                                                                                                                                                          (2, 'seojun@example.com', '$2a$10$8dVc84v3JUy3B042vtUO6uU4n02w3ksd7X0tuY9fJU0JwRseHXJXm', '김서준', TRUE,  TRUE,  '2026-10-02 10:00:00', '2026-10-02 10:00:00', '2026-10-02 10:00:00'),
                                                                                                                                                                          (3, 'yujin@example.com',  '$2a$10$8dVc84v3JUy3B042vtUO6uU4n02w3ksd7X0tuY9fJU0JwRseHXJXm', '이유진', TRUE,  FALSE, NULL,                  '2026-10-03 11:00:00', '2026-10-03 11:00:00'),
                                                                                                                                                                          (4, 'jiho@example.com',   '$2a$10$8dVc84v3JUy3B042vtUO6uU4n02w3ksd7X0tuY9fJU0JwRseHXJXm', '박지호', TRUE,  TRUE,  '2026-10-04 12:00:00', '2026-10-04 12:00:00', '2026-10-04 12:00:00'),
                                                                                                                                                                          (5, 'eunseo@example.com', '$2a$10$8dVc84v3JUy3B042vtUO6uU4n02w3ksd7X0tuY9fJU0JwRseHXJXm', '최은서', FALSE, FALSE, NULL,                  '2026-10-05 13:00:00', '2026-10-05 13:00:00');

-- 건강 프로필 -------------------------------------------------
-- 생활습관 컬럼의 값은 추천 설문 선택지의 value_code와 같습니다. (backend/docs/api/survey-api.md 5번 표)
INSERT INTO `health_profiles` (`user_id`, `birth_year`, `gender`, `height`, `weight`, `smoking`, `drinking`, `caffeine`, `diet_type`, `sun_exposure`, `updated_at`) VALUES
                                                                                                                                                                        (1, 1998, 'F', 163.0, 54.0, 'NONE', 'LOW',  'LOW',  'OMNI',       'LOW',    '2026-10-01 09:00:00'),
                                                                                                                                                                        (2, 1996, 'M', 176.5, 72.0, 'NONE', 'HIGH', 'HIGH', 'OMNI',       'NORMAL', '2026-10-02 10:00:00'),
                                                                                                                                                                        (3, 1999, 'F', 160.0, 50.0, 'NONE', 'NONE', 'LOW',  'VEGETARIAN', 'LOW',    '2026-10-03 11:00:00'),
                                                                                                                                                                        (4, 1971, 'M', 172.0, 78.5, 'NONE', 'LOW',  'LOW',  'OMNI',       'NORMAL', '2026-10-04 12:00:00'),
                                                                                                                                                                        (5, 2000, 'F', 165.0, 58.0, 'NONE', 'LOW',  'HIGH', 'OMNI',       'NORMAL', '2026-10-05 13:00:00');

-- 질환 (설문 Q-CONDITIONS의 value_code로 저장; 「없음」은 행 없음) ----------
-- 고지혈증은 설문 선택지에 없는 값이라 이름 그대로 둡니다.
INSERT INTO `user_conditions` (`user_condition_id`, `user_id`, `condition_name`, `created_at`) VALUES
                                                                                                   (1, 2, 'HYPERTENSION', '2026-10-02 10:00:00'),
                                                                                                   (2, 4, '고지혈증',     '2026-10-04 12:00:00'),
                                                                                                   (3, 4, 'DIABETES',     '2026-10-04 12:00:00');

-- 의약품 사전과 복용 중인 약 (예시 데이터, docs/sql/STEP02_seed_drugs.sql과 같은 내용) ----
-- drug_class 값은 recommendation_rules의 DRUG 조건 코드와 같다. D-011·D-012는 STEP 16부터 규칙 대상(R-30c3, R-36)인 예시 약물
-- D-013~D-016, D-U09~D-U12는 성분 확장(STEP 16)의 새 약물 계열 (ANTIPLATELET, INSULIN, IMMUNOSUPPRESSANT, ANTICONVULSANT, CHEMOTHERAPY)
INSERT INTO `drugs` (`drug_id`, `name`, `drug_class`) VALUES
                                                          ('D-001', '메트포르민',           'METFORMIN'),
                                                          ('D-002', '오메프라졸',           'PPI'),
                                                          ('D-003', '에날라프릴',           'ACE_INHIBITOR'),
                                                          ('D-004', '로사르탄',             'ARB'),
                                                          ('D-005', '스피로놀락톤',         'K_SPARING_DIURETIC'),
                                                          ('D-006', '히드로클로로티아지드', 'THIAZIDE_LOOP_DIURETIC'),
                                                          ('D-007', '와파린',               'WARFARIN'),
                                                          ('D-008', '레보티록신',           'LEVOTHYROXINE'),
                                                          ('D-009', '시프로플록사신',       'TETRA_QUINOLONE'),
                                                          ('D-010', '에스시탈로프람',       'SSRI'),
                                                          ('D-011', '암로디핀',             'CALCIUM_CHANNEL_BLOCKER'),
                                                          ('D-012', '아토르바스타틴',       'STATIN'),
                                                          ('D-013', '클로피도그렐',         'ANTIPLATELET'),
                                                          ('D-014', '인슐린',               'INSULIN'),
                                                          ('D-015', '타크로리무스',         'IMMUNOSUPPRESSANT'),
                                                          ('D-016', '발프로산',             'ANTICONVULSANT'),
                                                          ('D-U01', '혈압약 (이름 모름)',     'ANTIHYPERTENSIVE_UNKNOWN'),
                                                          ('D-U02', '이뇨제 (이름 모름)',     'DIURETIC_UNKNOWN'),
                                                          ('D-U03', '당뇨약 (이름 모름)',     'ANTIDIABETIC_UNKNOWN'),
                                                          ('D-U04', '위산억제제 (이름 모름)', 'ACID_REDUCER_UNKNOWN'),
                                                          ('D-U05', '항응고제 (이름 모름)',   'ANTICOAGULANT_UNKNOWN'),
                                                          ('D-U06', '갑상선약 (이름 모름)',   'THYROID_MED_UNKNOWN'),
                                                          ('D-U07', '항생제 (이름 모름)',     'ANTIBIOTIC_UNKNOWN'),
                                                          ('D-U08', '항우울제 (이름 모름)',   'ANTIDEPRESSANT_UNKNOWN'),
                                                          ('D-U09', '항혈소판제 (이름 모름)', 'ANTIPLATELET'),
                                                          ('D-U10', '항암제 (이름 모름)',     'CHEMOTHERAPY'),
                                                          ('D-U11', '면역억제제 (이름 모름)', 'IMMUNOSUPPRESSANT'),
                                                          ('D-U12', '뇌전증약 (이름 모름)',   'ANTICONVULSANT');

INSERT INTO `user_medications` (`user_medication_id`, `user_id`, `drug_id`, `unmatched_name`, `updated_at`) VALUES
                                                                                                                (1, 2, 'D-011', NULL,                   '2026-10-02 10:00:00'),
                                                                                                                (2, 4, 'D-001', NULL,                   '2026-10-04 12:00:00'),
                                                                                                                (3, 4, 'D-012', NULL,                   '2026-10-04 12:00:00'),
                                                                                                                (4, 5, NULL,           '병원에서 받은 약 (이름 모름)', '2026-10-05 13:00:00');

-- 건강기능식품 카탈로그 (가상 데이터) ---------------------------------
INSERT INTO `catalog_products` (`catalog_product_id`, `mfds_item_no`, `product_name`, `manufacturer`, `main_function`, `intake_method`, `intake_caution`, `image_url`, `created_at`) VALUES
                                                                                                                                                                                         (1, 'SAMPLE-0001', '원스데일리 종합비타민',   '샘플헬스',   '영양 보충',                         '1일 1회, 1정을 식후에 물과 함께 섭취', '특이체질, 알레르기 체질은 성분을 확인하세요.', NULL, '2026-10-01 00:00:00'),
                                                                                                                                                                                         (2, 'SAMPLE-0002', '초임계 알티지 오메가3',   '샘플바이오', '혈중 중성지질 개선, 혈행 개선에 도움',  '1일 1회, 1캡슐을 식후에 섭취',         '항응고제를 복용 중이면 전문가와 상담하세요.', NULL, '2026-10-01 00:00:00'),
                                                                                                                                                                                         (3, 'SAMPLE-0003', '킬레이트 마그네슘',       '샘플미네랄', '신경과 근육 기능 유지에 도움',        '1일 1회, 2캡슐을 식전에 섭취',         '신장 기능에 문제가 있으면 전문가와 상담하세요.', NULL, '2026-10-01 00:00:00'),
                                                                                                                                                                                         (4, 'SAMPLE-0004', '밀크씨슬 실리마린',       '샘플네이처', '간 건강에 도움을 줄 수 있음',          '1일 1회, 1캡슐을 식후에 섭취',         '알레르기 체질은 성분을 확인하세요.', NULL, '2026-10-01 00:00:00'),
                                                                                                                                                                                         (5, 'SAMPLE-0005', '활성 비타민B 컴플렉스',   '샘플헬스',   '에너지 대사에 필요',                  '1일 1회, 1정을 식후에 섭취',           '과량 섭취를 피하세요.', NULL, '2026-10-01 00:00:00'),
                                                                                                                                                                                         (6, 'SAMPLE-0006', '루테인 지아잔틴',         '샘플바이오', '노화로 인해 감소될 수 있는 황반색소밀도 유지에 도움', '1일 1회, 1캡슐을 식후에 섭취', '특이체질은 성분을 확인하세요.', NULL, '2026-10-01 00:00:00'),
                                                                                                                                                                                         (7, 'SAMPLE-0007', '비타민D 2000IU',          '샘플미네랄', '칼슘과 인의 흡수와 이용에 필요',      '1일 1회, 1정을 식후에 섭취',           '과량 섭취를 피하세요.', NULL, '2026-10-01 00:00:00'),
                                                                                                                                                                                         (8, 'SAMPLE-0008', '프로바이오틱스 유산균',   '샘플네이처', '유산균 증식 및 유해균 억제, 배변활동 원활에 도움', '1일 1회, 1캡슐을 식전에 섭취', '냉암소에 보관하세요.', NULL, '2026-10-01 00:00:00');

-- 영양제 카드 표기용 정보 (카테고리, 영양성분 요약, 용량, 참고 가격) — 모두 예시 값
UPDATE `catalog_products` SET `category` = '종합비타민', `ingredient_summary` = '비타민 21종 · 미네랄 7종', `package_size` = '60정 · 2개월분', `price` = 28900 WHERE `mfds_item_no` = 'SAMPLE-0001';
UPDATE `catalog_products` SET `category` = '오메가3', `ingredient_summary` = 'EPA+DHA 600mg', `package_size` = '30캡슐 · 1개월분', `price` = 21500 WHERE `mfds_item_no` = 'SAMPLE-0002';
UPDATE `catalog_products` SET `category` = '미네랄', `ingredient_summary` = '마그네슘 200mg', `package_size` = '120캡슐 · 2개월분', `price` = 32800 WHERE `mfds_item_no` = 'SAMPLE-0003';
UPDATE `catalog_products` SET `category` = '간 건강', `ingredient_summary` = '실리마린 130mg', `package_size` = '30캡슐 · 1개월분', `price` = 19800 WHERE `mfds_item_no` = 'SAMPLE-0004';
UPDATE `catalog_products` SET `category` = '비타민B', `ingredient_summary` = '비타민B군 8종', `package_size` = '60정 · 2개월분', `price` = 15900 WHERE `mfds_item_no` = 'SAMPLE-0005';
UPDATE `catalog_products` SET `category` = '눈 건강', `ingredient_summary` = '루테인 20mg · 지아잔틴 4mg', `package_size` = '30캡슐 · 1개월분', `price` = 24000 WHERE `mfds_item_no` = 'SAMPLE-0006';
UPDATE `catalog_products` SET `category` = '비타민D', `ingredient_summary` = '비타민D 2000IU', `package_size` = '90정 · 3개월분', `price` = 12900 WHERE `mfds_item_no` = 'SAMPLE-0007';
UPDATE `catalog_products` SET `category` = '유산균', `ingredient_summary` = '프로바이오틱스 100억 CFU', `package_size` = '30캡슐 · 1개월분', `price` = 26000 WHERE `mfds_item_no` = 'SAMPLE-0008';

-- 사용자가 등록한 영양제와 약 ----------------------------------------
-- 카탈로그에서 고르면 catalog_product_id, 직접 입력하면 manual_product_name에 저장
INSERT INTO `user_products` (`user_product_id`, `user_id`, `catalog_product_id`, `product_kind`, `manual_product_name`, `registered_via`, `start_date`, `is_active`, `created_at`) VALUES
                                                                                                                                                                                       (1,  1, 1,    'SUPPLEMENT', NULL,            'SEARCH', '2026-09-01', TRUE, '2026-10-01 09:10:00'),
                                                                                                                                                                                       (2,  1, 2,    'SUPPLEMENT', NULL,            'SEARCH', '2026-09-01', TRUE, '2026-10-01 09:11:00'),
                                                                                                                                                                                       (3,  1, 3,    'SUPPLEMENT', NULL,            'SEARCH', '2026-09-15', TRUE, '2026-10-01 09:12:00'),
                                                                                                                                                                                       (4,  2, 4,    'SUPPLEMENT', NULL,            'SEARCH', '2026-08-01', TRUE, '2026-10-02 10:10:00'),
                                                                                                                                                                                       (5,  2, 1,    'SUPPLEMENT', NULL,            'SEARCH', '2026-08-01', TRUE, '2026-10-02 10:11:00'),
                                                                                                                                                                                       (6,  2, NULL, 'MEDICINE',   '암로디핀 5mg',  'MANUAL', '2026-01-01', TRUE, '2026-10-02 10:12:00'),
                                                                                                                                                                                       (7,  3, 7,    'SUPPLEMENT', NULL,            'SEARCH', '2026-10-01', TRUE, '2026-10-03 11:10:00'),
                                                                                                                                                                                       (8,  4, 8,    'SUPPLEMENT', NULL,            'SEARCH', '2026-09-20', TRUE, '2026-10-04 12:10:00'),
                                                                                                                                                                                       (9,  4, NULL, 'MEDICINE',   '메트포르민',    'MANUAL', '2025-06-01', TRUE, '2026-10-04 12:11:00'),
                                                                                                                                                                                       (10, 5, 6,    'SUPPLEMENT', NULL,            'SEARCH', '2026-10-05', TRUE, '2026-10-05 13:10:00');

-- 복용 일정 -------------------------------------------------------
INSERT INTO `intake_schedules` (`schedule_id`, `user_product_id`, `meal_slot`, `meal_relation`, `remind_at`, `is_enabled`, `created_at`) VALUES
                                                                                                                                             (1, 1,  'BREAKFAST', 'AFTER',  '08:00:00', TRUE, '2026-10-01 09:20:00'),
                                                                                                                                             (2, 2,  'LUNCH',     'AFTER',  '12:30:00', TRUE, '2026-10-01 09:21:00'),
                                                                                                                                             (3, 3,  'DINNER',    'BEFORE', '19:00:00', TRUE, '2026-10-01 09:22:00'),
                                                                                                                                             (4, 4,  'BREAKFAST', 'AFTER',  '08:30:00', TRUE, '2026-10-02 10:20:00'),
                                                                                                                                             (5, 5,  'BREAKFAST', 'AFTER',  '08:30:00', TRUE, '2026-10-02 10:21:00'),
                                                                                                                                             (6, 6,  'BREAKFAST', 'AFTER',  '08:30:00', TRUE, '2026-10-02 10:22:00'),
                                                                                                                                             (7, 7,  'LUNCH',     'AFTER',  '12:00:00', TRUE, '2026-10-03 11:20:00'),
                                                                                                                                             (8, 8,  'BREAKFAST', 'BEFORE', '07:30:00', TRUE, '2026-10-04 12:20:00'),
                                                                                                                                             (9, 10, 'DINNER',    'AFTER',  '19:30:00', TRUE, '2026-10-05 13:20:00');

-- 복용 기록 (SKIPPED 1건 포함, 김민지 10/17 저녁) ---------------------
INSERT INTO `intake_records` (`intake_record_id`, `schedule_id`, `intake_date`, `status`, `completed_at`, `proof_image_url`, `is_shared`) VALUES
                                                                                                                                              (1, 1, '2026-10-15', 'TAKEN', '2026-10-15 08:03:00', NULL, FALSE),
                                                                                                                                              (2, 1, '2026-10-16', 'TAKEN', '2026-10-16 08:03:00', NULL, FALSE),
                                                                                                                                              (3, 1, '2026-10-17', 'TAKEN', '2026-10-17 08:03:00', NULL, FALSE),
                                                                                                                                              (4, 1, '2026-10-18', 'TAKEN', '2026-10-18 08:03:00', NULL, FALSE),
                                                                                                                                              (5, 1, '2026-10-19', 'TAKEN', '2026-10-19 08:03:00', NULL, FALSE),
                                                                                                                                              (6, 1, '2026-10-20', 'TAKEN', '2026-10-20 08:03:00', NULL, FALSE),
                                                                                                                                              (7, 2, '2026-10-15', 'TAKEN', '2026-10-15 12:33:00', NULL, FALSE),
                                                                                                                                              (8, 2, '2026-10-16', 'TAKEN', '2026-10-16 12:33:00', NULL, FALSE),
                                                                                                                                              (9, 2, '2026-10-17', 'TAKEN', '2026-10-17 12:33:00', NULL, FALSE),
                                                                                                                                              (10, 2, '2026-10-18', 'TAKEN', '2026-10-18 12:33:00', NULL, FALSE),
                                                                                                                                              (11, 2, '2026-10-19', 'TAKEN', '2026-10-19 12:33:00', NULL, FALSE),
                                                                                                                                              (12, 2, '2026-10-20', 'TAKEN', '2026-10-20 12:33:00', NULL, FALSE),
                                                                                                                                              (13, 3, '2026-10-15', 'TAKEN', '2026-10-15 19:03:00', NULL, FALSE),
                                                                                                                                              (14, 3, '2026-10-16', 'TAKEN', '2026-10-16 19:03:00', NULL, FALSE),
                                                                                                                                              (15, 3, '2026-10-17', 'SKIPPED', NULL, NULL, FALSE),
                                                                                                                                              (16, 3, '2026-10-18', 'TAKEN', '2026-10-18 19:03:00', NULL, FALSE),
                                                                                                                                              (17, 3, '2026-10-19', 'TAKEN', '2026-10-19 19:03:00', NULL, FALSE),
                                                                                                                                              (18, 3, '2026-10-20', 'TAKEN', '2026-10-20 19:03:00', NULL, FALSE),
                                                                                                                                              (19, 1, '2026-10-21', 'TAKEN', '2026-10-21 08:05:00', NULL, FALSE),
                                                                                                                                              (20, 4, '2026-10-18', 'TAKEN', '2026-10-18 08:33:00', NULL, FALSE),
                                                                                                                                              (21, 4, '2026-10-19', 'TAKEN', '2026-10-19 08:33:00', NULL, FALSE),
                                                                                                                                              (22, 4, '2026-10-20', 'TAKEN', '2026-10-20 08:33:00', NULL, FALSE),
                                                                                                                                              (23, 4, '2026-10-21', 'TAKEN', '2026-10-21 08:33:00', NULL, FALSE),
                                                                                                                                              (24, 5, '2026-10-18', 'TAKEN', '2026-10-18 08:33:00', NULL, FALSE),
                                                                                                                                              (25, 5, '2026-10-19', 'TAKEN', '2026-10-19 08:33:00', NULL, FALSE),
                                                                                                                                              (26, 5, '2026-10-20', 'TAKEN', '2026-10-20 08:33:00', NULL, FALSE),
                                                                                                                                              (27, 5, '2026-10-21', 'TAKEN', '2026-10-21 08:33:00', NULL, FALSE),
                                                                                                                                              (28, 6, '2026-10-18', 'TAKEN', '2026-10-18 08:33:00', NULL, FALSE),
                                                                                                                                              (29, 6, '2026-10-19', 'TAKEN', '2026-10-19 08:33:00', NULL, FALSE),
                                                                                                                                              (30, 6, '2026-10-20', 'TAKEN', '2026-10-20 08:33:00', NULL, FALSE),
                                                                                                                                              (31, 6, '2026-10-21', 'TAKEN', '2026-10-21 08:33:00', NULL, FALSE),
                                                                                                                                              (32, 7, '2026-10-19', 'TAKEN', '2026-10-19 12:03:00', NULL, FALSE),
                                                                                                                                              (33, 7, '2026-10-20', 'TAKEN', '2026-10-20 12:03:00', NULL, FALSE),
                                                                                                                                              (34, 8, '2026-10-17', 'TAKEN', '2026-10-17 07:33:00', NULL, FALSE),
                                                                                                                                              (35, 8, '2026-10-18', 'TAKEN', '2026-10-18 07:33:00', NULL, FALSE),
                                                                                                                                              (36, 8, '2026-10-19', 'TAKEN', '2026-10-19 07:33:00', NULL, FALSE),
                                                                                                                                              (37, 8, '2026-10-20', 'TAKEN', '2026-10-20 07:33:00', NULL, FALSE),
                                                                                                                                              (38, 9, '2026-10-20', 'TAKEN', '2026-10-20 19:33:00', NULL, FALSE);

-- 알림 --------------------------------------------------------
-- notification_type은 NotificationType enum 상수명과 같아야 합니다. (INTAKE_REMINDER / FRIEND_GOAL_ACHIEVED / NEW_FOLLOWER)
-- dedup_key 형식: INTAKE:{받는사람}:{날짜}:{시각} / FRIEND_GOAL:{받는사람}:{완료한사람}:{날짜} / FOLLOW:{받는사람}:{팔로우한사람}
-- 1: 김민지 점심 복용 알림 / 2: 김민지가 팔로우하는 김서준(follows 1)의 복용 완료 / 3: 이유진의 팔로우 요청(follows 6, PENDING)
INSERT INTO `notifications` (`notification_id`, `user_id`, `schedule_id`, `actor_user_id`, `notification_type`, `title`, `scheduled_at`, `sent_at`, `read_at`, `created_at`, `dedup_key`) VALUES
                                                                                                                                                                                              (1, 1, 2,    NULL, 'INTAKE_REMINDER',      '점심 영양제, 챙겨 드셨나요?',       '2026-10-21 12:30:00', '2026-10-21 12:30:00', NULL,                  '2026-10-21 12:30:00', 'INTAKE:1:2026-10-21:12:30'),
                                                                                                                                                                                              (2, 1, NULL, 2,    'FRIEND_GOAL_ACHIEVED', '김서준님이 오늘의 약속을 완료했어요', NULL,                  '2026-10-21 09:10:00', NULL,                  '2026-10-21 09:10:00', 'FRIEND_GOAL:1:2:2026-10-21'),
                                                                                                                                                                                              (3, 1, NULL, 3,    'NEW_FOLLOWER',         '이유진님이 팔로우를 요청했어요',      NULL,                  '2026-10-20 15:00:00', '2026-10-20 18:00:00', '2026-10-20 15:00:00', 'FOLLOW:1:3');

-- 팔로우 (김민지는 4명을 팔로우, 3→1은 수락 대기, 5→3은 거절됨) ------
INSERT INTO `follows` (`follow_id`, `follower_id`, `following_id`, `status`, `created_at`) VALUES
                                                                                               (1, 1, 2, 'ACCEPTED', '2026-10-06 10:00:00'),
                                                                                               (2, 1, 3, 'ACCEPTED', '2026-10-06 10:01:00'),
                                                                                               (3, 1, 4, 'ACCEPTED', '2026-10-06 10:02:00'),
                                                                                               (4, 1, 5, 'ACCEPTED', '2026-10-06 10:03:00'),
                                                                                               (5, 2, 1, 'ACCEPTED', '2026-10-07 09:00:00'),
                                                                                               (6, 3, 1, 'PENDING',  '2026-10-20 15:00:00'),
                                                                                               (7, 4, 5, 'ACCEPTED', '2026-10-08 09:00:00'),
                                                                                               (8, 5, 3, 'REJECTED', '2026-10-09 09:00:00');

-- 안부지킴이 ------------------------------------------------------
INSERT INTO `guardian_settings` (`guardian_setting_id`, `user_id`, `guardian_user_id`, `inactivity_days`, `consented_at`, `is_active`, `last_notified_at`) VALUES
                                                                                                                                                               (1, 4, 5, 3, '2026-10-08 09:30:00', TRUE,  '2026-10-20 09:00:00'),
                                                                                                                                                               (2, 3, 1, 5, '2026-10-10 18:00:00', TRUE,  NULL),
                                                                                                                                                               (3, 2, 1, 3, NULL,                  FALSE, NULL);

-- 폐의약품 수거 위치 (가상 데이터, 좌표는 대략적인 예시) ----------------
INSERT INTO `disposal_locations` (`disposal_location_id`, `location_name`, `location_type`, `address`, `latitude`, `longitude`, `source_name`, `source_updated_at`) VALUES
                                                                                                                                                                        (1, '건강온누리약국',         'PHARMACY',    '서울 마포구 월드컵북로 21',  37.5790000, 126.8990000, '샘플데이터', '2026-10-01 00:00:00'),
                                                                                                                                                                        (2, '상암햇살약국',           'PHARMACY',    '서울 마포구 성암로 267',     37.5805000, 126.8935000, '샘플데이터', '2026-10-01 00:00:00'),
                                                                                                                                                                        (3, '우리동네365약국',        'PHARMACY',    '서울 마포구 매봉산로 75',    37.5778000, 126.8960000, '샘플데이터', '2026-10-01 00:00:00'),
                                                                                                                                                                        (4, '디지털미디어시티약국',   'PHARMACY',    '서울 마포구 월드컵북로 396', 37.5775000, 126.8995000, '샘플데이터', '2026-10-01 00:00:00'),
                                                                                                                                                                        (5, '상암동 폐의약품 수거함', 'BOX',         '서울 마포구 상암동 샘플로 1', 37.5820000, 126.8970000, '샘플데이터', '2026-10-01 00:00:00'),
                                                                                                                                                                        (6, '상암우체국 우체통',      'POST_OFFICE', '서울 마포구 상암동 샘플로 10', 37.5812000, 126.8980000, '샘플데이터', '2026-10-01 00:00:00');

-- 추천 설문 v1 (예시 데이터, docs/sql/STEP02_seed_survey_v1.sql과 같은 내용) ------------
-- 문항 문구는 바꿔도 되지만 code와 value_code는 AI 추천 코드가 기준으로 씁니다.
INSERT INTO `surveys` (`survey_id`, `version`, `status`) VALUES
    (1, 'v1', 'ACTIVE');

INSERT INTO `survey_questions` (`question_id`, `survey_id`, `code`, `question_text`, `answer_type`, `profile_key`, `is_sensitive`, `guest_visible`, `display_order`) VALUES
                                                                                                                                                                         (1, 1, 'Q-GENDER', '성별을 알려주세요', 'SINGLE', 'gender', FALSE, TRUE, 1),
                                                                                                                                                                         (2, 1, 'Q-BIRTH', '출생 연도를 알려주세요', 'NUMBER', 'birth_year', FALSE, TRUE, 2),
                                                                                                                                                                         (3, 1, 'Q-SMOKING', '흡연하시나요?', 'SINGLE', 'smoking', FALSE, TRUE, 3),
                                                                                                                                                                         (4, 1, 'Q-DRINKING', '술은 얼마나 자주 드시나요?', 'SINGLE', 'drinking', FALSE, TRUE, 4),
                                                                                                                                                                         (5, 1, 'Q-CAFFEINE', '커피·차는 얼마나 드시나요?', 'SINGLE', 'caffeine', FALSE, TRUE, 5),
                                                                                                                                                                         (6, 1, 'Q-DIET', '어떤 식단을 드시나요?', 'SINGLE', 'diet_type', FALSE, TRUE, 6),
                                                                                                                                                                         (7, 1, 'Q-SUN', '햇빛을 얼마나 쬐시나요?', 'SINGLE', 'sun_exposure', FALSE, TRUE, 7),
                                                                                                                                                                         (8, 1, 'Q-SLEEP', '최근 수면 상태는 어떤가요?', 'SINGLE', NULL, FALSE, TRUE, 8),
                                                                                                                                                                         (9, 1, 'Q-FATIGUE', '최근 피로감은 어느 정도인가요?', 'SINGLE', NULL, FALSE, TRUE, 9),
                                                                                                                                                                         (10, 1, 'Q-GOAL', '원하는 효과를 모두 골라주세요', 'MULTI', NULL, FALSE, TRUE, 10),
                                                                                                                                                                         (11, 1, 'Q-PREGNANCY', '임신·수유 중이신가요?', 'SINGLE', NULL, TRUE, FALSE, 11),
                                                                                                                                                                         (12, 1, 'Q-CONDITIONS', '진단받은 질환을 모두 골라주세요', 'MULTI', 'conditions', TRUE, FALSE, 12),
                                                                                                                                                                         (13, 1, 'Q-MEDICATIONS', '복용 중인 약을 알려주세요', 'TEXT', 'medications', TRUE, FALSE, 13),
                                                                                                                                                                         (14, 1, 'Q-SUPPLEMENTS', '복용 중인 영양제 성분과 함량을 알려주세요', 'TEXT', NULL, FALSE, TRUE, 14);

INSERT INTO `question_options` (`option_id`, `question_id`, `value_code`, `label`, `display_order`) VALUES
                                                                                                        (1, 1, 'M', '남성', 1),
                                                                                                        (2, 1, 'F', '여성', 2),
                                                                                                        (3, 3, 'NONE', '안 피움', 1),
                                                                                                        (4, 3, 'HALF_PACK', '하루 반 갑 이하', 2),
                                                                                                        (5, 3, 'ONE_PACK', '하루 1갑', 3),
                                                                                                        (6, 3, 'TWO_PACK_PLUS', '하루 2갑 이상', 4),
                                                                                                        (7, 4, 'NONE', '안 마심', 1),
                                                                                                        (8, 4, 'LOW', '가끔', 2),
                                                                                                        (9, 4, 'HIGH', '자주', 3),
                                                                                                        (10, 5, 'NONE', '안 마심', 1),
                                                                                                        (11, 5, 'LOW', '하루 1~2잔', 2),
                                                                                                        (12, 5, 'HIGH', '하루 3잔 이상', 3),
                                                                                                        (13, 6, 'OMNI', '일반식', 1),
                                                                                                        (14, 6, 'VEGETARIAN', '채식(유제품·달걀 포함)', 2),
                                                                                                        (15, 6, 'VEGAN', '비건', 3),
                                                                                                        (16, 7, 'LOW', '거의 실내에 있어요', 1),
                                                                                                        (17, 7, 'NORMAL', '보통이에요', 2),
                                                                                                        (18, 8, 'GOOD', '좋아요', 1),
                                                                                                        (19, 8, 'NORMAL', '보통이에요', 2),
                                                                                                        (20, 8, 'POOR', '잘 못 자요', 3),
                                                                                                        (21, 9, 'LOW', '거의 없어요', 1),
                                                                                                        (22, 9, 'NORMAL', '보통이에요', 2),
                                                                                                        (23, 9, 'HIGH', '자주 피곤해요', 3),
                                                                                                        (24, 10, 'SLEEP', '수면', 1),
                                                                                                        (25, 10, 'FATIGUE', '피로 회복', 2),
                                                                                                        (26, 10, 'IMMUNITY', '면역', 3),
                                                                                                        (27, 10, 'BONE', '뼈 건강', 4),
                                                                                                        (28, 10, 'EYE', '눈 건강', 5),
                                                                                                        (29, 11, 'NOT_APPLICABLE', '해당 없음', 1),
                                                                                                        (30, 11, 'POSSIBLE', '임신 가능성 있음', 2),
                                                                                                        (31, 11, 'PREGNANT', '임신 중', 3),
                                                                                                        (32, 11, 'LACTATING', '수유 중', 4),
                                                                                                        (33, 12, 'NONE', '없음', 1),
                                                                                                        (34, 12, 'HYPERTENSION', '고혈압', 2),
                                                                                                        (35, 12, 'DIABETES', '당뇨', 3),
                                                                                                        (36, 12, 'THYROID', '갑상선 질환', 4),
                                                                                                        (37, 12, 'KIDNEY', '신장 질환', 5);

-- 설문 응답: 1번은 김민지(회원, 민감 항목 포함), 2번은 비회원. 일부 문항만 답한 예시 ------------
INSERT INTO `survey_responses` (`response_id`, `survey_id`, `user_id`, `respondent_type`, `includes_sensitive`, `status`, `submitted_at`) VALUES
                                                                                                                                              (1, 1, 1,    'MEMBER', TRUE,  'SUBMITTED', '2026-10-21 10:00:00'),
                                                                                                                                              (2, 1, NULL, 'GUEST',  FALSE, 'SUBMITTED', '2026-10-21 11:00:00');

-- 1번: 여성(2), 1998년생, 비흡연(3), 자주 피곤(23), 원하는 효과 피로 회복(25), 임신·수유 해당 없음(29)
-- 2번: 남성(1), 잘 못 잠(20), 원하는 효과 수면(24)
INSERT INTO `survey_answers` (`answer_id`, `response_id`, `question_id`, `option_id`, `value_number`, `value_text`) VALUES
                                                                                                                        (1, 1, 1,  2,    NULL,    NULL),
                                                                                                                        (2, 1, 2,  NULL, 1998.00, NULL),
                                                                                                                        (3, 1, 3,  3,    NULL,    NULL),
                                                                                                                        (4, 1, 9,  23,   NULL,    NULL),
                                                                                                                        (5, 1, 10, 25,   NULL,    NULL),
                                                                                                                        (6, 1, 11, 29,   NULL,    NULL),
                                                                                                                        (7, 2, 1,  1,    NULL,    NULL),
                                                                                                                        (8, 2, 8,  20,   NULL,    NULL),
                                                                                                                        (9, 2, 10, 24,   NULL,    NULL);

-- AI 추천 결과 (김민지의 설문 응답 기준). ingredient_name은 성분 코드(IngredientCode), 용량·규칙 컬럼은 기본값 ----
INSERT INTO `ai_recommendations` (`recommendation_id`, `response_id`, `summary`, `model_name`, `created_at`) VALUES
    (1, 1, '자주 피곤하다고 답해주신 내용을 중심으로 비타민 B12와 마그네슘을 추천해요.', 'sample-model', '2026-10-21 10:00:05');

INSERT INTO `ai_recommendation_items` (`recommendation_item_id`, `recommendation_id`, `ingredient_name`, `reason`, `sort_order`) VALUES
                                                                                                                                     (1, 1, 'VITAMIN_B12', '에너지 대사에 필요한 영양소예요.', 1),
                                                                                                                                     (2, 1, 'MAGNESIUM',   '신경과 근육 기능 유지에 도움을 줄 수 있어요.', 2);