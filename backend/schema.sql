-- 약쏙해줘 3주 MVP ERD v2 / MySQL 8.0.16+
-- Core 14 tables: code-friendly logical separation; for medical interaction checks see README.
-- 건강/복용 정보는 민감 데이터: 회원별 접근 통제, 로그 제한, 삭제/보존 정책 필요.
CREATE DATABASE IF NOT EXISTS yakssok CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE yakssok;

-- ============================================================
-- 기존 테이블 삭제 (FK 의존 순서: 자식 -> 부모)
-- 주의: 실행 시 기존 데이터가 모두 삭제됩니다.
-- ============================================================
DROP TABLE IF EXISTS `ai_recommendation_items`;
DROP TABLE IF EXISTS `ai_recommendations`;
DROP TABLE IF EXISTS `health_surveys`;
DROP TABLE IF EXISTS `guardian_settings`;
DROP TABLE IF EXISTS `follows`;
DROP TABLE IF EXISTS `notifications`;
DROP TABLE IF EXISTS `intake_records`;
DROP TABLE IF EXISTS `intake_schedules`;
DROP TABLE IF EXISTS `user_products`;
DROP TABLE IF EXISTS `catalog_products`;
DROP TABLE IF EXISTS `user_conditions`;
DROP TABLE IF EXISTS `health_profiles`;
DROP TABLE IF EXISTS `disposal_locations`;
DROP TABLE IF EXISTS `users`;

-- ============================================================
-- 테이블 생성
-- ============================================================
CREATE TABLE `users` (
  `user_id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `email` VARCHAR(255) NOT NULL UNIQUE,
  `password_hash` VARCHAR(255) NOT NULL,
  `nickname` VARCHAR(50) NOT NULL UNIQUE,
  `share_intake_status` BOOLEAN NOT NULL DEFAULT FALSE,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE `health_profiles` (
  `user_id` BIGINT PRIMARY KEY,
  `birth_year` SMALLINT NULL,
  `gender` VARCHAR(10) NULL,
  `height` DECIMAL(5,1) NULL,
  `weight` DECIMAL(5,1) NULL,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT ck_birth_year CHECK (birth_year IS NULL OR (birth_year BETWEEN 1900 AND 2100)),
  CONSTRAINT ck_measures CHECK ((height IS NULL OR height > 0) AND (weight IS NULL OR weight > 0)),
  CONSTRAINT `fk_health_profiles_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `user_conditions` (
  `user_condition_id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `condition_name` VARCHAR(70) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_user_condition (user_id, condition_name),
  CONSTRAINT `fk_user_conditions_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `catalog_products` (
  `catalog_product_id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `mfds_item_no` VARCHAR(100) NULL UNIQUE,
  `product_name` VARCHAR(255) NOT NULL,
  `manufacturer` VARCHAR(255) NULL,
  `main_function` TEXT NULL,
  `intake_method` TEXT NULL,
  `intake_caution` TEXT NULL,
  `image_url` VARCHAR(1000) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE `user_products` (
  `user_product_id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `catalog_product_id` BIGINT NULL,
  `product_kind` VARCHAR(15) NOT NULL,
  `manual_product_name` VARCHAR(255) NULL,
  `registered_via` VARCHAR(15) NOT NULL DEFAULT 'SEARCH',
  `start_date` DATE NULL,
  `is_active` BOOLEAN NOT NULL DEFAULT TRUE,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_product_kind CHECK (product_kind IN ('SUPPLEMENT','MEDICINE')),
  CONSTRAINT ck_registration_via CHECK (registered_via IN ('SEARCH','MANUAL','OCR')),
  CONSTRAINT ck_catalog_or_manual CHECK ((catalog_product_id IS NOT NULL AND manual_product_name IS NULL AND product_kind='SUPPLEMENT') OR (catalog_product_id IS NULL AND manual_product_name IS NOT NULL)),
  KEY idx_user_products_user_active (user_id, is_active),
  CONSTRAINT `fk_user_products_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_products_catalog_product_id` FOREIGN KEY (`catalog_product_id`) REFERENCES `catalog_products` (`catalog_product_id`) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE `intake_schedules` (
  `schedule_id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_product_id` BIGINT NOT NULL,
  `meal_slot` VARCHAR(15) NOT NULL,
  `meal_relation` VARCHAR(10) NOT NULL,
  `remind_at` TIME NOT NULL,
  `is_enabled` BOOLEAN NOT NULL DEFAULT TRUE,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_meal_slot CHECK (meal_slot IN ('BREAKFAST','LUNCH','DINNER')),
  CONSTRAINT ck_meal_relation CHECK (meal_relation IN ('BEFORE','AFTER','ANY')),
  KEY idx_remind_enabled_time (is_enabled, remind_at),
  CONSTRAINT `fk_intake_schedules_user_product_id` FOREIGN KEY (`user_product_id`) REFERENCES `user_products` (`user_product_id`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `intake_records` (
  `intake_record_id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `schedule_id` BIGINT NOT NULL,
  `intake_date` DATE NOT NULL,
  `status` VARCHAR(10) NOT NULL,
  `completed_at` DATETIME NULL,
  `proof_image_url` VARCHAR(1000) NULL,
  `is_shared` BOOLEAN NOT NULL DEFAULT FALSE,
  UNIQUE KEY uq_schedule_intake_date (schedule_id, intake_date),
  CONSTRAINT ck_intake_status CHECK (status IN ('TAKEN','SKIPPED')),
  KEY idx_intake_date (intake_date),
  CONSTRAINT `fk_intake_records_schedule_id` FOREIGN KEY (`schedule_id`) REFERENCES `intake_schedules` (`schedule_id`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `notifications` (
  `notification_id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `schedule_id` BIGINT NULL,
  `actor_user_id` BIGINT NULL,
  `notification_type` VARCHAR(30) NOT NULL,
  `title` VARCHAR(150) NOT NULL,
  `scheduled_at` DATETIME NULL,
  `sent_at` DATETIME NULL,
  `read_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `dedup_key` VARCHAR(150) NOT NULL UNIQUE,
  CONSTRAINT ck_notif_kind CHECK (notification_type IN ('INTAKE_REMINDER','FRIEND_GOAL_ACHIEVED','NEW_FOLLOWER')),
  KEY idx_notifications_inbox (user_id, read_at, created_at),
  CONSTRAINT `fk_notifications_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_notifications_schedule_id` FOREIGN KEY (`schedule_id`) REFERENCES `intake_schedules` (`schedule_id`) ON DELETE SET NULL,
  CONSTRAINT `fk_notifications_actor_user_id` FOREIGN KEY (`actor_user_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE `follows` (
  `follow_id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `follower_id` BIGINT NOT NULL,
  `following_id` BIGINT NOT NULL,
  `status` VARCHAR(10) NOT NULL DEFAULT 'PENDING',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_follow_pair (follower_id, following_id),
  CONSTRAINT ck_follow_distinct CHECK (follower_id <> following_id),
  CONSTRAINT ck_follow_status CHECK (status IN ('PENDING','ACCEPTED','REJECTED')),
  CONSTRAINT `fk_follows_follower_id` FOREIGN KEY (`follower_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_follows_following_id` FOREIGN KEY (`following_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `guardian_settings` (
  `guardian_setting_id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `guardian_user_id` BIGINT NOT NULL,
  `inactivity_days` INT NOT NULL DEFAULT 3,
  `consented_at` DATETIME NULL,
  `is_active` BOOLEAN NOT NULL DEFAULT FALSE,
  `last_notified_at` DATETIME NULL,
  UNIQUE KEY uq_guardian_pair (user_id, guardian_user_id),
  CONSTRAINT ck_guardian_distinct CHECK (user_id <> guardian_user_id),
  CONSTRAINT ck_guardian_days CHECK (inactivity_days BETWEEN 1 AND 30),
  CONSTRAINT ck_guardian_consent CHECK (is_active = FALSE OR consented_at IS NOT NULL),
  CONSTRAINT `fk_guardian_settings_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_guardian_settings_guardian_user_id` FOREIGN KEY (`guardian_user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `health_surveys` (
  `survey_id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `answers_json` JSON NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT `fk_health_surveys_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `ai_recommendations` (
  `recommendation_id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `survey_id` BIGINT NOT NULL,
  `summary` TEXT NULL,
  `model_name` VARCHAR(60) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT `fk_ai_recommendations_survey_id` FOREIGN KEY (`survey_id`) REFERENCES `health_surveys` (`survey_id`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `ai_recommendation_items` (
  `recommendation_item_id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `recommendation_id` BIGINT NOT NULL,
  `ingredient_name` VARCHAR(150) NOT NULL,
  `reason` TEXT NULL,
  `sort_order` INT NOT NULL DEFAULT 1,
  UNIQUE KEY uq_recommendation_ingredient (recommendation_id, ingredient_name),
  CONSTRAINT `fk_ai_recommendation_items_recommendation_id` FOREIGN KEY (`recommendation_id`) REFERENCES `ai_recommendations` (`recommendation_id`) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE `disposal_locations` (
  `disposal_location_id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `location_name` VARCHAR(200) NOT NULL,
  `location_type` VARCHAR(20) NOT NULL,
  `address` VARCHAR(500) NOT NULL,
  `latitude` DECIMAL(10,7) NOT NULL,
  `longitude` DECIMAL(10,7) NOT NULL,
  `source_name` VARCHAR(100) NULL,
  `source_updated_at` DATETIME NULL,
  CONSTRAINT ck_disposal_type CHECK (location_type IN ('BOX','POST_OFFICE','PHARMACY')),
  CONSTRAINT ck_lat_lng CHECK (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180),
  KEY idx_disposal_lat_lng (latitude, longitude)
) ENGINE=InnoDB;

-- "없음" 질환 선택: user_conditions 행 없이 표시.
-- 건강기능식품 catalog_products는 MFDS API 기준. 처방약은 user_products에 이름 수동 등록.
-- AI는 추천 성분을 문자열로 저장하며 처방/의학적 확정을 의미하지 않음.
-- 실제 성분 상충 자동 판정: 검증된 의약품/성분 데이터 확보 후 별도 확장.
-- 알림 내역만으로 백그라운드 웹 푸시는 제공되지 않음: 별도 Push API 구현.
-- 마지막 접속(last_active_at) 기준 안부지킴이. 사용자가 명시적으로 수락해야 활성화.