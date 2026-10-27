CREATE TABLE `health_profiles` (
	`user_id`	BIGINT	NOT NULL,
	`birth_year`	SMALLINT	NULL,
	`gender`	VARCHAR(10)	NULL,
	`height`	DECIMAL(5,1)	NULL,
	`weight`	DECIMAL(5,1)	NULL,
	`smoking`	VARCHAR(20)	NULL,
	`drinking`	VARCHAR(20)	NULL,
	`caffeine`	VARCHAR(20)	NULL,
	`diet_type`	VARCHAR(20)	NULL,
	`sun_exposure`	VARCHAR(20)	NULL,
	`updated_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `user_conditions` (
	`user_condition_id`	BIGINT	NOT NULL,
	`user_id`	BIGINT	NOT NULL,
	`condition_name`	VARCHAR(70)	NOT NULL,
	`created_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `intake_records` (
	`intake_record_id`	BIGINT	NOT NULL,
	`schedule_id`	BIGINT	NOT NULL,
	`intake_date`	DATE	NOT NULL,
	`status`	VARCHAR(10)	NOT NULL,
	`completed_at`	DATETIME	NULL,
	`proof_image_url`	VARCHAR(1000)	NULL,
	`is_shared`	BOOLEAN	NOT NULL	DEFAULT FALSE
);

CREATE TABLE `users` (
	`user_id`	BIGINT	NOT NULL,
	`email`	VARCHAR(255)	NOT NULL,
	`password_hash`	VARCHAR(255)	NOT NULL,
	`nickname`	VARCHAR(50)	NOT NULL,
	`share_intake_status`	BOOLEAN	NOT NULL	DEFAULT FALSE,
	`sensitive_consent`	BOOLEAN	NOT NULL	DEFAULT FALSE,
	`sensitive_consent_at`	DATETIME	NULL,
	`created_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP,
	`updated_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `user_products` (
	`user_product_id`	BIGINT	NOT NULL,
	`user_id`	BIGINT	NOT NULL,
	`catalog_product_id`	BIGINT	NULL,
	`product_kind`	VARCHAR(15)	NOT NULL,
	`manual_product_name`	VARCHAR(255)	NULL,
	`registered_via`	VARCHAR(15)	NOT NULL	DEFAULT 'SEARCH',
	`start_date`	DATE	NULL,
	`is_active`	BOOLEAN	NOT NULL	DEFAULT TRUE,
	`created_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `catalog_products` (
	`catalog_product_id`	BIGINT	NOT NULL,
	`mfds_item_no`	VARCHAR(100)	NULL,
	`product_name`	VARCHAR(255)	NOT NULL,
	`manufacturer`	VARCHAR(255)	NULL,
	`main_function`	TEXT	NULL,
	`intake_method`	TEXT	NULL,
	`intake_caution`	TEXT	NULL,
	`image_url`	VARCHAR(1000)	NULL,
	`created_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `notifications` (
	`notification_id`	BIGINT	NOT NULL,
	`user_id`	BIGINT	NOT NULL,
	`schedule_id`	BIGINT	NULL,
	`actor_user_id`	BIGINT	NULL,
	`notification_type`	VARCHAR(30)	NOT NULL,
	`title`	VARCHAR(150)	NOT NULL,
	`scheduled_at`	DATETIME	NULL,
	`sent_at`	DATETIME	NULL,
	`read_at`	DATETIME	NULL,
	`created_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP,
	`dedup_key`	VARCHAR(150)	NULL
);

CREATE TABLE `guardian_settings` (
	`guardian_setting_id`	BIGINT	NOT NULL,
	`user_id`	BIGINT	NOT NULL,
	`guardian_user_id`	BIGINT	NOT NULL,
	`inactivity_days`	INT	NOT NULL	DEFAULT 3,
	`consented_at`	DATETIME	NULL,
	`is_active`	BOOLEAN	NOT NULL	DEFAULT FALSE,
	`last_notified_at`	DATETIME	NULL
);

CREATE TABLE `follows` (
	`follow_id`	BIGINT	NOT NULL,
	`follower_id`	BIGINT	NOT NULL,
	`following_id`	BIGINT	NOT NULL,
	`status`	VARCHAR(10)	NOT NULL	DEFAULT 'PENDING',
	`created_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `disposal_locations` (
	`disposal_location_id`	BIGINT	NOT NULL,
	`location_name`	VARCHAR(200)	NOT NULL,
	`location_type`	VARCHAR(20)	NOT NULL,
	`address`	VARCHAR(500)	NOT NULL,
	`latitude`	DECIMAL(10,7)	NOT NULL,
	`longitude`	DECIMAL(10,7)	NOT NULL,
	`source_name`	VARCHAR(100)	NULL,
	`source_updated_at`	DATETIME	NULL
);

CREATE TABLE `ai_recommendation_items` (
	`recommendation_item_id`	BIGINT	NOT NULL,
	`recommendation_id`	BIGINT	NOT NULL,
	`ingredient_name`	VARCHAR(150)	NOT NULL,
	`reason`	TEXT	NULL,
	`sort_order`	INT	NOT NULL	DEFAULT 1
);

CREATE TABLE `ai_recommendations` (
	`recommendation_id`	BIGINT	NOT NULL,
	`response_id`	BIGINT	NOT NULL,
	`summary`	TEXT	NULL,
	`model_name`	VARCHAR(60)	NULL,
	`created_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `intake_schedules` (
	`schedule_id`	BIGINT	NOT NULL,
	`user_product_id`	BIGINT	NOT NULL,
	`meal_slot`	VARCHAR(15)	NOT NULL,
	`meal_relation`	VARCHAR(10)	NOT NULL,
	`remind_at`	TIME	NOT NULL,
	`is_enabled`	BOOLEAN	NOT NULL	DEFAULT TRUE,
	`created_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE `surveys` (
	`survey_id`	BIGINT	NOT NULL,
	`version`	VARCHAR(20)	NOT NULL,
	`status`	VARCHAR(10)	NOT NULL	DEFAULT 'DRAFT'
);

CREATE TABLE `survey_questions` (
	`question_id`	BIGINT	NOT NULL,
	`survey_id`	BIGINT	NOT NULL,
	`code`	VARCHAR(20)	NOT NULL,
	`question_text`	TEXT	NOT NULL,
	`answer_type`	VARCHAR(10)	NOT NULL,
	`profile_key`	VARCHAR(50)	NULL,
	`is_sensitive`	BOOLEAN	NOT NULL	DEFAULT FALSE,
	`guest_visible`	BOOLEAN	NOT NULL	DEFAULT TRUE,
	`display_order`	INT	NOT NULL
);

CREATE TABLE `question_options` (
	`option_id`	BIGINT	NOT NULL,
	`question_id`	BIGINT	NOT NULL,
	`value_code`	VARCHAR(50)	NOT NULL,
	`label`	VARCHAR(100)	NOT NULL,
	`display_order`	INT	NOT NULL
);

CREATE TABLE `survey_responses` (
	`response_id`	BIGINT	NOT NULL,
	`survey_id`	BIGINT	NOT NULL,
	`user_id`	BIGINT	NULL,
	`respondent_type`	VARCHAR(10)	NOT NULL,
	`includes_sensitive`	BOOLEAN	NOT NULL	DEFAULT FALSE,
	`status`	VARCHAR(15)	NOT NULL	DEFAULT 'IN_PROGRESS',
	`submitted_at`	DATETIME	NULL
);

CREATE TABLE `survey_answers` (
	`answer_id`	BIGINT	NOT NULL,
	`response_id`	BIGINT	NOT NULL,
	`question_id`	BIGINT	NOT NULL,
	`option_id`	BIGINT	NULL,
	`value_number`	DECIMAL(10,2)	NULL,
	`value_text`	TEXT	NULL
);

CREATE TABLE `drugs` (
	`drug_id`	VARCHAR(50)	NOT NULL,
	`name`	VARCHAR(150)	NOT NULL,
	`drug_class`	VARCHAR(50)	NOT NULL
);

CREATE TABLE `user_medications` (
	`user_medication_id`	BIGINT	NOT NULL,
	`user_id`	BIGINT	NOT NULL,
	`drug_id`	VARCHAR(50)	NULL,
	`unmatched_name`	VARCHAR(150)	NULL,
	`updated_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP
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

ALTER TABLE `notifications` ADD CONSTRAINT `PK_NOTIFICATIONS` PRIMARY KEY (
	`notification_id`
);

ALTER TABLE `guardian_settings` ADD CONSTRAINT `PK_GUARDIAN_SETTINGS` PRIMARY KEY (
	`guardian_setting_id`
);

ALTER TABLE `follows` ADD CONSTRAINT `PK_FOLLOWS` PRIMARY KEY (
	`follow_id`
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

-- PK 자동 증가 (PK 다음, FK 이전에 걸어야 MySQL에서 오류가 나지 않음)
ALTER TABLE `user_conditions` MODIFY `user_condition_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `intake_records` MODIFY `intake_record_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `users` MODIFY `user_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `user_products` MODIFY `user_product_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `catalog_products` MODIFY `catalog_product_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `notifications` MODIFY `notification_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `guardian_settings` MODIFY `guardian_setting_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `follows` MODIFY `follow_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `disposal_locations` MODIFY `disposal_location_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `ai_recommendation_items` MODIFY `recommendation_item_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `ai_recommendations` MODIFY `recommendation_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `intake_schedules` MODIFY `schedule_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `surveys` MODIFY `survey_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `survey_questions` MODIFY `question_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `question_options` MODIFY `option_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `survey_responses` MODIFY `response_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `survey_answers` MODIFY `answer_id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `user_medications` MODIFY `user_medication_id` BIGINT NOT NULL AUTO_INCREMENT;

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
