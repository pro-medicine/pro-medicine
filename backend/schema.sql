-- 약쏙해줘 스키마 v3 + 예시 데이터 / MySQL 8.0
-- ============================================================
-- 기존 테이블 삭제 (FK 의존 순서: 자식 -> 부모)
-- 주의: 실행하면 기존 데이터가 모두 삭제됩니다.
-- ============================================================
DROP TABLE IF EXISTS `survey_answers`;
DROP TABLE IF EXISTS `question_options`;
DROP TABLE IF EXISTS `survey_questions`;
DROP TABLE IF EXISTS `ai_recommendation_items`;
DROP TABLE IF EXISTS `ai_recommendations`;
DROP TABLE IF EXISTS `survey_responses`;
DROP TABLE IF EXISTS `surveys`;
DROP TABLE IF EXISTS `user_medications`;
DROP TABLE IF EXISTS `drugs`;
DROP TABLE IF EXISTS `health_profiles`;
DROP TABLE IF EXISTS `user_conditions`;
DROP TABLE IF EXISTS `intake_records`;
DROP TABLE IF EXISTS `intake_schedules`;
DROP TABLE IF EXISTS `user_products`;
DROP TABLE IF EXISTS `catalog_products`;
DROP TABLE IF EXISTS `notifications`;
DROP TABLE IF EXISTS `guardian_settings`;
DROP TABLE IF EXISTS `follows`;
DROP TABLE IF EXISTS `disposal_locations`;
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
                         `updated_at`   DATETIME   NOT NULL   DEFAULT CURRENT_TIMESTAMP
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
                                    `created_at`   DATETIME   NOT NULL   DEFAULT CURRENT_TIMESTAMP
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
                                 CONSTRAINT `ck_notif_kind` CHECK (`notification_type` IN ('INTAKE_REMINDER','FRIEND_GOAL_ACHIEVED','NEW_FOLLOWER', 'FOLLOW_ACCEPTED'))
);

CREATE TABLE `guardian_settings` (
                                     `guardian_setting_id`   BIGINT   NOT NULL,
                                     `user_id`   BIGINT   NOT NULL,
                                     `guardian_user_id`   BIGINT   NOT NULL,
                                     `inactivity_days`   INT   NOT NULL   DEFAULT 3,
                                     `consented_at`   DATETIME   NULL,
                                     `is_active`   BOOLEAN   NOT NULL   DEFAULT FALSE,
                                     `last_notified_at`   DATETIME   NULL
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

CREATE TABLE `ai_recommendation_items` (
                                           `recommendation_item_id`   BIGINT   NOT NULL,
                                           `recommendation_id`   BIGINT   NOT NULL,
                                           `ingredient_name`   VARCHAR(150)   NOT NULL,
                                           `reason`   TEXT   NULL,
                                           `sort_order`   INT   NOT NULL   DEFAULT 1
);

CREATE TABLE `ai_recommendations` (
                                      `recommendation_id`   BIGINT   NOT NULL,
                                      `response_id`   BIGINT   NOT NULL,
                                      `summary`   TEXT   NULL,
                                      `model_name`   VARCHAR(60)   NULL,
                                      `created_at`   DATETIME   NOT NULL   DEFAULT CURRENT_TIMESTAMP
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

ALTER TABLE `user_products` ADD CONSTRAINT `PK_USER_PRODUCTS` PRIMARY KEY (
                                                                           `user_product_id`
    );

ALTER TABLE `catalog_products` ADD CONSTRAINT `PK_CATALOG_PRODUCTS` PRIMARY KEY (
                                                                                 `catalog_product_id`
    );

ALTER TABLE `guardian_settings` ADD CONSTRAINT `PK_GUARDIAN_SETTINGS` PRIMARY KEY (
                                                                                   `guardian_setting_id`
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

-- ============================================================
-- 예시 데이터 (개발/시연용)
--  * 모든 계정의 비밀번호: password1234!  (BCrypt 해시 저장)
--  * 기준일: 2026-10-21 (오늘 김민지는 3개 중 1개 복용 -> 33%)
--  * 제품명/제조사/수거 위치/좌표는 전부 가상의 샘플입니다.
--  * PK를 직접 지정합니다. (AUTO_INCREMENT가 없는 현재 스키마 기준)
-- ============================================================

-- 사용자 (5명) ------------------------------------------------
INSERT INTO `users` (`user_id`, `email`, `password_hash`, `nickname`, `share_intake_status`, `sensitive_consent`, `sensitive_consent_at`, `created_at`, `updated_at`) VALUES
                                                                                                                                                                          (1, 'minji@example.com',  '$2a$10$8dVc84v3JUy3B042vtUO6uU4n02w3ksd7X0tuY9fJU0JwRseHXJXm', '김민지', TRUE,  TRUE,  '2026-10-01 09:00:00', '2026-10-01 09:00:00', '2026-10-01 09:00:00'),
                                                                                                                                                                          (2, 'seojun@example.com', '$2a$10$8dVc84v3JUy3B042vtUO6uU4n02w3ksd7X0tuY9fJU0JwRseHXJXm', '김서준', TRUE,  TRUE,  '2026-10-02 10:00:00', '2026-10-02 10:00:00', '2026-10-02 10:00:00'),
                                                                                                                                                                          (3, 'yujin@example.com',  '$2a$10$8dVc84v3JUy3B042vtUO6uU4n02w3ksd7X0tuY9fJU0JwRseHXJXm', '이유진', TRUE,  FALSE, NULL,                  '2026-10-03 11:00:00', '2026-10-03 11:00:00'),
                                                                                                                                                                          (4, 'jiho@example.com',   '$2a$10$8dVc84v3JUy3B042vtUO6uU4n02w3ksd7X0tuY9fJU0JwRseHXJXm', '박지호', TRUE,  TRUE,  '2026-10-04 12:00:00', '2026-10-04 12:00:00', '2026-10-04 12:00:00'),
                                                                                                                                                                          (5, 'eunseo@example.com', '$2a$10$8dVc84v3JUy3B042vtUO6uU4n02w3ksd7X0tuY9fJU0JwRseHXJXm', '최은서', FALSE, FALSE, NULL,                  '2026-10-05 13:00:00', '2026-10-05 13:00:00');

-- 건강 프로필 -------------------------------------------------
-- 생활습관 컬럼의 값(NONE, LOW 등)은 예시입니다. 실제 코드값은 팀에서 정한 대로 맞추세요.
INSERT INTO `health_profiles` (`user_id`, `birth_year`, `gender`, `height`, `weight`, `smoking`, `drinking`, `caffeine`, `diet_type`, `sun_exposure`, `updated_at`) VALUES
                                                                                                                                                                        (1, 1998, 'FEMALE', 163.0, 54.0, 'NONE',   'OCCASIONAL', 'MEDIUM', 'EATING_OUT', 'LOW',    '2026-10-01 09:00:00'),
                                                                                                                                                                        (2, 1996, 'MALE',   176.5, 72.0, 'FORMER', 'FREQUENT',   'HIGH',   'BALANCED',   'MEDIUM', '2026-10-02 10:00:00'),
                                                                                                                                                                        (3, 1999, 'FEMALE', 160.0, 50.0, 'NONE',   'NONE',       'LOW',    'LOW_VEGGIE', 'LOW',    '2026-10-03 11:00:00'),
                                                                                                                                                                        (4, 1971, 'MALE',   172.0, 78.5, 'NONE',   'OCCASIONAL', 'MEDIUM', 'BALANCED',   'MEDIUM', '2026-10-04 12:00:00'),
                                                                                                                                                                        (5, 2000, 'FEMALE', 165.0, 58.0, 'NONE',   'OCCASIONAL', 'HIGH',   'BALANCED',   'HIGH',   '2026-10-05 13:00:00');

-- 질환약 분류 (가입 시 선택한 값; 「없음」은 행 없음) ----------------
INSERT INTO `user_conditions` (`user_condition_id`, `user_id`, `condition_name`, `created_at`) VALUES
                                                                                                   (1, 2, '고혈압',   '2026-10-02 10:00:00'),
                                                                                                   (2, 4, '고지혈증', '2026-10-04 12:00:00'),
                                                                                                   (3, 4, '당뇨',     '2026-10-04 12:00:00');

-- 의약품 사전과 복용 중인 약 ---------------------------------------
INSERT INTO `drugs` (`drug_id`, `name`, `drug_class`) VALUES
                                                          ('AMLODIPINE',   '암로디핀',       'CALCIUM_CHANNEL_BLOCKER'),
                                                          ('METFORMIN',    '메트포르민',     'BIGUANIDE'),
                                                          ('ATORVASTATIN', '아토르바스타틴', 'STATIN'),
                                                          ('WARFARIN',     '와파린',         'ANTICOAGULANT');

INSERT INTO `user_medications` (`user_medication_id`, `user_id`, `drug_id`, `unmatched_name`, `updated_at`) VALUES
                                                                                                                (1, 2, 'AMLODIPINE',   NULL,                   '2026-10-02 10:00:00'),
                                                                                                                (2, 4, 'METFORMIN',    NULL,                   '2026-10-04 12:00:00'),
                                                                                                                (3, 4, 'ATORVASTATIN', NULL,                   '2026-10-04 12:00:00'),
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

-- 추천 설문 (버전 v1) -----------------------------------------------
INSERT INTO `surveys` (`survey_id`, `version`, `status`) VALUES
    (1, 'v1', 'ACTIVE');

INSERT INTO `survey_questions` (`question_id`, `survey_id`, `code`, `question_text`, `answer_type`, `profile_key`, `is_sensitive`, `guest_visible`, `display_order`) VALUES
                                                                                                                                                                         (1, 1, 'CONCERN',  '요즘 가장 신경 쓰이는 건강 고민은 무엇인가요?', 'SINGLE', NULL,        FALSE, TRUE,  1),
                                                                                                                                                                         (2, 1, 'DIET',     '평소 식습관은 어떤 편인가요?',                  'SINGLE', 'diet_type', FALSE, TRUE,  2),
                                                                                                                                                                         (3, 1, 'EXERCISE', '일주일에 운동을 얼마나 하나요?',                'SINGLE', NULL,        FALSE, TRUE,  3),
                                                                                                                                                                         (4, 1, 'SMOKING',  '흡연을 하시나요?',                              'SINGLE', 'smoking',   TRUE,  FALSE, 4);

INSERT INTO `question_options` (`option_id`, `question_id`, `value_code`, `label`, `display_order`) VALUES
                                                                                                        (1,  1, 'FATIGUE',    '쉽게 피로해요',             1),
                                                                                                        (2,  1, 'SLEEP',      '잠을 푹 못 자요',           2),
                                                                                                        (3,  1, 'EYE',        '눈이 자주 피로해요',        3),
                                                                                                        (4,  1, 'GUT',        '장 건강이 걱정돼요',        4),
                                                                                                        (5,  2, 'REGULAR',    '규칙적으로 골고루 먹어요',  1),
                                                                                                        (6,  2, 'EATING_OUT', '외식과 배달이 잦아요',      2),
                                                                                                        (7,  2, 'LOW_VEGGIE', '채소를 잘 안 먹어요',       3),
                                                                                                        (8,  2, 'SKIP_MEALS', '식사를 자주 거르는 편이에요', 4),
                                                                                                        (9,  3, 'NONE',       '거의 하지 않아요',          1),
                                                                                                        (10, 3, 'LOW',        '주 1~2회',                  2),
                                                                                                        (11, 3, 'MID',        '주 3~4회',                  3),
                                                                                                        (12, 3, 'HIGH',       '주 5회 이상',               4),
                                                                                                        (13, 4, 'NONE',       '비흡연',                    1),
                                                                                                        (14, 4, 'FORMER',     '과거에 흡연했어요',         2),
                                                                                                        (15, 4, 'CURRENT',    '현재 흡연 중이에요',        3);

-- 설문 응답: 1번은 김민지(회원, 민감 항목 포함), 2번은 비회원 ------------
INSERT INTO `survey_responses` (`response_id`, `survey_id`, `user_id`, `respondent_type`, `includes_sensitive`, `status`, `submitted_at`) VALUES
                                                                                                                                              (1, 1, 1,    'MEMBER', TRUE,  'SUBMITTED', '2026-10-21 10:00:00'),
                                                                                                                                              (2, 1, NULL, 'GUEST',  FALSE, 'SUBMITTED', '2026-10-21 11:00:00');

INSERT INTO `survey_answers` (`answer_id`, `response_id`, `question_id`, `option_id`, `value_number`, `value_text`) VALUES
                                                                                                                        (1, 1, 1, 1,  NULL, NULL),
                                                                                                                        (2, 1, 2, 6,  NULL, NULL),
                                                                                                                        (3, 1, 3, 9,  NULL, NULL),
                                                                                                                        (4, 1, 4, 13, NULL, NULL),
                                                                                                                        (5, 2, 1, 2,  NULL, NULL),
                                                                                                                        (6, 2, 2, 5,  NULL, NULL),
                                                                                                                        (7, 2, 3, 11, NULL, NULL);

-- AI 추천 결과 (김민지의 설문 응답 기준) -------------------------------
INSERT INTO `ai_recommendations` (`recommendation_id`, `response_id`, `summary`, `model_name`, `created_at`) VALUES
    (1, 1, '쉽게 피로해요라고 답해주신 내용을 중심으로 비타민B군과 마그네슘을 추천해요.', 'sample-model', '2026-10-21 10:00:05');

INSERT INTO `ai_recommendation_items` (`recommendation_item_id`, `recommendation_id`, `ingredient_name`, `reason`, `sort_order`) VALUES
                                                                                                                                     (1, 1, '비타민 B군', '에너지 대사에 필요한 영양소예요. 외식이 잦으면 부족해지기 쉬워요.', 1),
                                                                                                                                     (2, 1, '마그네슘',   '신경과 근육 기능 유지에 도움을 줄 수 있어요.', 2);