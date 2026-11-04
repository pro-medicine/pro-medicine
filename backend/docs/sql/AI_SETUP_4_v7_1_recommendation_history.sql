-- AI_SETUP_4: V7.1 마이페이지 "추천 기록" (최근 20건 + 추천받을 때의 제품 순위) DB 변경 (2026-11-03)
-- 실행 위치: 각자 PC의 MySQL과 배포 DB (yakssok DB 선택 후 실행). MySQL Workbench에서 파일을 열어 전체 실행해도 된다
--   예) mysql -u root -p --default-character-set=utf8mb4 yakssok < backend/docs/sql/AI_SETUP_4_v7_1_recommendation_history.sql
-- 실행 순서
--   1) AI_SETUP.sql · AI_SETUP_2.sql · AI_SETUP_3_v1_survey_order.sql (이미 실행했으면 건너뛴다)
--   2) 이 파일. 새 서버를 띄우기 전·후 어느 쪽이어도 된다 (서버 ddl-auto가 먼저 만든 컬럼·키·테이블은 건너뛰고,
--      서버가 바꾸지 못하는 부분(chat_session_id NULL 허용·FK SET NULL·기존 행 user_id 채우기)만 맞춘다)
--   ※ 배포 DB에는 AI_SETUP.sql을 다시 실행하지 않는다 (설문 응답·추천 기록이 지워짐). 이 파일만 실행한다
-- 하는 일 (AI 테이블만. users·catalog_products는 FK로 참조만 하고 구조·데이터를 바꾸지 않는다)
--   ① ai_recommendations: user_id 컬럼(회원 추천의 회원, 비회원 NULL) + 키 + FK(users, ON DELETE SET NULL)
--      기존 행 채우기: V1 설문 응답 → V2 맞춤 질문 회차 → V4 대화 세션의 회원
--      (이미 90일이 지나 회차·대화 세션이 지워진 예전 V2·V4 추천은 회원을 알 수 없어 NULL로 남는다)
--   ② ai_recommendation_products 새 테이블: 추천 성분 카드별 제품 순위 1~3위 + 추천 당시 같이 든 성분·안전 경고
--   ③ ai_chat_results: user_id 컬럼 + 키 + FK(users, SET NULL), 기존 행 채우기,
--      chat_session_id NULL 허용 + FK를 ON DELETE CASCADE → SET NULL로 바꿈 (이름이 다른 CASCADE FK가 있어도 모두 지움)
--      (90일 뒤 대화 세션·원문은 지워지고 결과 행(cards, 사용자 문장 없음)은 남는다)
--   - 테이블을 지우지 않는다. 기존 설문 응답·AI 추천 기록·회원 데이터는 그대로 남는다
-- 여러 번 실행해도 결과가 같다 (컬럼·키·FK·테이블이 있으면 건너뜀, user_id가 NULL인 행만 채움).
-- MySQL Workbench safe update 모드도 파일 안에서 잠깐 끄고 원래 값으로 되돌린다
-- 새로 셋업하는 DB는 AI_SETUP.sql에 이미 반영되어 있어 이 파일을 실행해도 바뀌는 것이 없다
SET NAMES utf8mb4;
SET @old_safe_updates = @@SQL_SAFE_UPDATES;
SET SQL_SAFE_UPDATES = 0;

-- ============================================================
-- ① ai_recommendations: 회원 연결 (V1·V2·V4 트랙 2)
-- ============================================================
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_recommendations' AND COLUMN_NAME = 'user_id') = 0,
    'ALTER TABLE `ai_recommendations` ADD COLUMN `user_id` BIGINT NULL COMMENT ''회원 추천의 회원 (비회원 NULL, V7.1)'' AFTER `chat_session_id`',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_recommendations' AND INDEX_NAME = 'idx_ai_recommendations_user') = 0,
    'ALTER TABLE `ai_recommendations` ADD KEY `idx_ai_recommendations_user` (`user_id`, `finished_at`)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `ai_recommendations` r JOIN `survey_responses` s ON r.`response_id` = s.`response_id`
   SET r.`user_id` = s.`user_id`
 WHERE r.`user_id` IS NULL AND s.`user_id` IS NOT NULL;
UPDATE `ai_recommendations` r JOIN `ai_question_sessions` q ON r.`session_id` = q.`session_id`
   SET r.`user_id` = q.`user_id`
 WHERE r.`user_id` IS NULL;
UPDATE `ai_recommendations` r JOIN `ai_chat_sessions` c ON r.`chat_session_id` = c.`chat_session_id`
   SET r.`user_id` = c.`user_id`
 WHERE r.`user_id` IS NULL;

-- 회원 탈퇴 처리는 후속 결정 (지금은 SET NULL: 탈퇴 기능이 생겨도 삭제가 막히지 않게)
SET @sql = IF((SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_recommendations' AND COLUMN_NAME = 'user_id'
                 AND REFERENCED_TABLE_NAME = 'users') = 0,
    'ALTER TABLE `ai_recommendations` ADD CONSTRAINT `fk_ai_recommendations_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- ② 추천 성분 카드별 제품 순위 (새 테이블). 성분 순서 = ai_recommendation_items.sort_order, 제품 순서 = product_rank
-- ============================================================
CREATE TABLE IF NOT EXISTS `ai_recommendation_products` (
  `recommendation_product_id` bigint NOT NULL AUTO_INCREMENT,
  `recommendation_item_id` bigint NOT NULL COMMENT '어느 추천의 어느 성분 카드인지',
  `catalog_product_id` bigint NOT NULL,
  `product_rank` int NOT NULL COMMENT '성분 카드 안 순위 1~3',
  `also_contains` json DEFAULT NULL COMMENT '같은 추천의 다른 성분 중 이 제품에 든 것 (추천 시점)',
  `warnings` json DEFAULT NULL COMMENT '안전 경고 (추천 시점)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`recommendation_product_id`),
  UNIQUE KEY `uq_ai_rec_product_rank` (`recommendation_item_id`,`product_rank`),
  KEY `fk_ai_rec_products_product` (`catalog_product_id`),
  CONSTRAINT `fk_ai_rec_products_item` FOREIGN KEY (`recommendation_item_id`) REFERENCES `ai_recommendation_items` (`recommendation_item_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ai_rec_products_product` FOREIGN KEY (`catalog_product_id`) REFERENCES `catalog_products` (`catalog_product_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- ③ ai_chat_results: 회원 연결 + 세션이 지워져도 결과 보존 (V4 트랙 1)
-- ============================================================
SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chat_results' AND COLUMN_NAME = 'user_id') = 0,
    'ALTER TABLE `ai_chat_results` ADD COLUMN `user_id` BIGINT NULL COMMENT ''대화한 회원 (세션 삭제 후에도 유지, V7.1)'' AFTER `chat_session_id`',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chat_results' AND INDEX_NAME = 'idx_chat_results_user') = 0,
    'ALTER TABLE `ai_chat_results` ADD KEY `idx_chat_results_user` (`user_id`, `created_at`)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `ai_chat_results` r JOIN `ai_chat_sessions` c ON r.`chat_session_id` = c.`chat_session_id`
   SET r.`user_id` = c.`user_id`
 WHERE r.`user_id` IS NULL;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chat_results' AND COLUMN_NAME = 'user_id'
                 AND REFERENCED_TABLE_NAME = 'users') = 0,
    'ALTER TABLE `ai_chat_results` ADD CONSTRAINT `fk_chat_results_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- chat_session_id에 걸린 FK 중 SET NULL이 아닌 것(CASCADE 등)을 이름과 상관없이 모두 지운다
-- (fk_chat_results_session 말고도 서버 ddl-auto가 예전에 만든 FKxxxx 이름의 CASCADE FK가 남아 있는 DB가 있음)
-- → NULL 허용 → fk_chat_results_session을 SET NULL로 다시 건다 (이미 SET NULL이면 건너뜀)
SET @drops = (SELECT GROUP_CONCAT(CONCAT('DROP FOREIGN KEY `', r.CONSTRAINT_NAME, '`') SEPARATOR ', ')
              FROM information_schema.REFERENTIAL_CONSTRAINTS r
              JOIN information_schema.KEY_COLUMN_USAGE k
                ON k.CONSTRAINT_SCHEMA = r.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME = r.CONSTRAINT_NAME
               AND k.TABLE_NAME = r.TABLE_NAME
              WHERE r.CONSTRAINT_SCHEMA = DATABASE() AND r.TABLE_NAME = 'ai_chat_results'
                AND k.COLUMN_NAME = 'chat_session_id' AND r.DELETE_RULE <> 'SET NULL');
SET @sql = IF(@drops IS NULL, 'DO 0', CONCAT('ALTER TABLE `ai_chat_results` ', @drops));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE `ai_chat_results` MODIFY `chat_session_id` BIGINT NULL COMMENT '90일 삭제 후 NULL (V7.1부터 결과 행은 남음)';

SET @sql = IF((SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chat_results'
                 AND CONSTRAINT_NAME = 'fk_chat_results_session') = 0,
    'ALTER TABLE `ai_chat_results` ADD CONSTRAINT `fk_chat_results_session` FOREIGN KEY (`chat_session_id`) REFERENCES `ai_chat_sessions` (`chat_session_id`) ON DELETE SET NULL',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET SQL_SAFE_UPDATES = @old_safe_updates;

-- 확인: user_id가 채워진 행 수, 세션 FK 동작(SET NULL), 새 테이블
SELECT 'ai_recommendations' AS tbl, COUNT(*) AS total_rows, SUM(`user_id` IS NOT NULL) AS with_user FROM `ai_recommendations`
UNION ALL
SELECT 'ai_chat_results', COUNT(*), SUM(`user_id` IS NOT NULL) FROM `ai_chat_results`;
SELECT CONSTRAINT_NAME, TABLE_NAME, DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND CONSTRAINT_NAME IN ('fk_chat_results_session', 'fk_chat_results_user', 'fk_ai_recommendations_user',
                          'fk_ai_rec_products_item', 'fk_ai_rec_products_product')
ORDER BY CONSTRAINT_NAME;
