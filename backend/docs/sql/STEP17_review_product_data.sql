-- STEP 17: V3 약사 리뷰 기반 제품 추천 (MySQL)
-- 실행 위치: 각자 PC의 MySQL (yakssok DB 선택 후 실행). MySQL Workbench에서 파일을 열어 전체 실행해도 된다
--   예) mysql -u root -p yakssok < backend/docs/sql/STEP17_review_product_data.sql
-- 실행 대상
--   - 이미 쓰는 DB: STEP13 → STEP14 → STEP16 다음에 이 파일을 한 번 실행한다
--   - 처음 세팅하는 DB: schema.sql에 이미 포함 (실행하지 않아도 되고, 실행해도 결과가 같다)
-- 반드시 실행: 새 테이블 3개는 엔티티 없이 읽기 전용(JdbcTemplate)으로 조회하므로 ddl-auto가 만들지 않는다
-- 여러 번 실행해도 안전하다 (IF NOT EXISTS, 컬럼·인덱스가 없을 때만 추가)
-- 영향받는 기능
--   - 리뷰 제품 추천 API (GET /api/v1/recommendations/{id}/products, .../availability)
--   - catalog_products: 컬럼 추가만 (모두 NULL 허용 또는 기본값 있음) → 주연님 검색·상세·내 영양제 기능에는 영향 없음
--   - catalog_products.catalog_product_id AUTO_INCREMENT 보정 (엔티티는 IDENTITY인데 schema.sql에 빠져 있었음)
-- 데이터는 리뷰 파이프라인(레포 밖)이 만든 review_import_YYYY-MM.sql로만 넣는다 (Git에 올리지 않음)

-- ============================================================
-- 1) catalog_products 컬럼 추가 (없을 때만)
-- ============================================================
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'catalog_products' AND column_name = 'name_ko') = 0,
    'ALTER TABLE `catalog_products` ADD COLUMN `name_ko` VARCHAR(255) NULL COMMENT ''화면 표시용 한글 이름. 없으면 product_name''', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'catalog_products' AND column_name = 'brand') = 0,
    'ALTER TABLE `catalog_products` ADD COLUMN `brand` VARCHAR(255) NULL', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'catalog_products' AND column_name = 'data_source') = 0,
    'ALTER TABLE `catalog_products` ADD COLUMN `data_source` VARCHAR(20) NOT NULL DEFAULT ''SAMPLE'' COMMENT ''DSLD, MFDS, MANUAL, SAMPLE''', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'catalog_products' AND column_name = 'external_id') = 0,
    'ALTER TABLE `catalog_products` ADD COLUMN `external_id` VARCHAR(64) NULL COMMENT ''DSLD 라벨 ID / 품목제조신고번호''', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'catalog_products' AND column_name = 'raw_materials') = 0,
    'ALTER TABLE `catalog_products` ADD COLUMN `raw_materials` TEXT NULL COMMENT ''원재료 원문 (검수용)''', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'catalog_products' AND column_name = 'is_active') = 0,
    'ALTER TABLE `catalog_products` ADD COLUMN `is_active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT ''단종·비노출이면 FALSE''', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'catalog_products' AND column_name = 'blocked_kr') = 0,
    'ALTER TABLE `catalog_products` ADD COLUMN `blocked_kr` BOOLEAN NOT NULL DEFAULT FALSE COMMENT ''국내 반입 차단 성분(멜라토닌 등) 포함''', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'catalog_products' AND column_name = 'image_key') = 0,
    'ALTER TABLE `catalog_products` ADD COLUMN `image_key` VARCHAR(500) NULL COMMENT ''S3 키 (리팩토링 때 사용)''', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'catalog_products' AND column_name = 'updated_at') = 0,
    'ALTER TABLE `catalog_products` ADD COLUMN `updated_at` DATETIME NULL COMMENT ''리뷰 데이터 import 시각''', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 월별 upsert 키. external_id가 NULL인 기존 행(예시 데이터·검색 등록 제품)은 서로 겹쳐도 된다 (MySQL UNIQUE는 NULL 중복 허용)
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'catalog_products' AND index_name = 'uk_catalog_products_source_external') = 0,
    'ALTER TABLE `catalog_products` ADD UNIQUE KEY `uk_catalog_products_source_external` (`data_source`, `external_id`)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2) catalog_product_id AUTO_INCREMENT 보정 (엔티티 CatalogProduct는 IDENTITY)
-- user_products 등이 FK로 참조하면 PK 컬럼 MODIFY가 오류(1833)로 막히므로 STEP13과 같이 잠시 FK 검사를 끈다.
-- 다시 실행해도 결과는 같다
-- ============================================================
SET FOREIGN_KEY_CHECKS = 0;
ALTER TABLE `catalog_products` MODIFY `catalog_product_id` BIGINT NOT NULL AUTO_INCREMENT;
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 3) 새 테이블 (서버는 읽기만, 쓰기는 월 1회 SQL import)
-- ingredient_code: IngredientCode 24개 + 코드 없는 라벨 성분은 OTHER
-- ============================================================
-- 제품 1 : 성분 N (라벨 기준). 안전 필터와 "같이 든 추천 성분"에 쓴다
CREATE TABLE IF NOT EXISTS `product_ingredients` (
    `product_ingredient_id` BIGINT        NOT NULL AUTO_INCREMENT,
    `catalog_product_id`    BIGINT        NOT NULL,
    `ingredient_code`       VARCHAR(30)   NOT NULL COMMENT 'IngredientCode 24개 또는 OTHER',
    `ingredient_name`       VARCHAR(100)  NOT NULL COMMENT '라벨 원래 이름 (Red Yeast Rice 등)',
    `amount_per_day`        DECIMAL(12,3) NULL COMMENT 'DSLD만. NULL = 들어 있음',
    `unit`                  VARCHAR(20)   NULL,
    `source`                VARCHAR(20)   NULL COMMENT 'DSLD_LABEL, MFDS_FUNCTIONAL, MANUAL',
    PRIMARY KEY (`product_ingredient_id`),
    UNIQUE KEY `uk_product_ingredients` (`catalog_product_id`, `ingredient_code`, `ingredient_name`),
    CONSTRAINT `fk_product_ingredients_product` FOREIGN KEY (`catalog_product_id`)
        REFERENCES `catalog_products` (`catalog_product_id`) ON DELETE CASCADE
);

-- 리뷰 언급 1건 = 1행 (LLM 추출 + context 임베딩). V3 요청 경로에서는 읽지 않는다 (점수 계산·V4·검수용)
CREATE TABLE IF NOT EXISTS `review_mentions` (
    `mention_id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `video_id`           VARCHAR(20)  NOT NULL,
    `mention_no`         INT          NOT NULL,
    `channel_id`         VARCHAR(30)  NOT NULL COMMENT '약사 채널 (점수 계산용, 화면 노출 안 함)',
    `published_at`       DATE         NULL,
    `catalog_product_id` BIGINT       NULL COMMENT '제품 확정 못 하면 NULL',
    `resolve_status`     VARCHAR(20)  NOT NULL COMMENT 'RESOLVED, PENDING_REVIEW',
    `product_name_ko`    VARCHAR(255) NULL COMMENT '리뷰 표기 (등록 대기 목록용)',
    `brand_en`           VARCHAR(255) NULL,
    `ingredients`        JSON         NULL COMMENT '["MAGNESIUM"] 어떤 성분 제품으로 말했나',
    `stance`             VARCHAR(15)  NOT NULL COMMENT 'RECOMMEND, NOT_RECOMMEND, NEUTRAL',
    `is_sponsored`       BOOLEAN      NOT NULL DEFAULT FALSE,
    `purpose_tags`       JSON         NULL COMMENT '["SLEEP"] V4용',
    `cautions`           JSON         NULL COMMENT '[{category, text}] 숫자 없음',
    `context`            VARCHAR(400) NULL COMMENT 'LLM이 정리한 누구에게·왜 (V4 검색용, API로 내보내지 않음)',
    `embedding`          MEDIUMBLOB   NULL COMMENT 'context 벡터 float32 x 1536 (V4용)',
    `embedding_model`    VARCHAR(80)  NULL,
    `start_sec`          INT          NULL COMMENT '검수용',
    `evidence_quote`     TEXT         NULL COMMENT '검수용, API로 내보내지 않음',
    `prompt_version`     VARCHAR(30)  NULL,
    PRIMARY KEY (`mention_id`),
    UNIQUE KEY `uk_review_mentions_video` (`video_id`, `mention_no`),
    KEY `idx_review_mentions_product` (`catalog_product_id`),
    CONSTRAINT `fk_review_mentions_product` FOREIGN KEY (`catalog_product_id`)
        REFERENCES `catalog_products` (`catalog_product_id`) ON DELETE SET NULL
);

-- 성분 x 제품 리뷰 인기 점수. import 때 review_mentions로 통째로 다시 계산, 서버는 읽기만
CREATE TABLE IF NOT EXISTS `review_product_scores` (
    `ingredient_code`        VARCHAR(30)  NOT NULL COMMENT '리뷰에서 추천된 성분',
    `catalog_product_id`     BIGINT       NOT NULL,
    `base_score`             DECIMAL(8,3) NOT NULL COMMENT '인기 점수 (설계 5-1)',
    `recommend_channels`     INT          NOT NULL DEFAULT 0 COMMENT '동점 2순위',
    `not_recommend_channels` INT          NOT NULL DEFAULT 0 COMMENT '동점 4순위',
    `last_published_at`      DATE         NULL COMMENT '동점 3순위',
    `common_cautions`        JSON         NULL COMMENT '2명 이상 약사가 말한 주의점 ["..."]',
    `mention_ids`            JSON         NULL COMMENT '근거 언급 (V4·검수용)',
    `computed_at`            DATETIME     NULL COMMENT '데이터 버전',
    PRIMARY KEY (`ingredient_code`, `catalog_product_id`),
    KEY `idx_review_product_scores_rank` (`ingredient_code`, `base_score` DESC),
    KEY `idx_review_product_scores_product` (`catalog_product_id`),
    CONSTRAINT `fk_review_product_scores_product` FOREIGN KEY (`catalog_product_id`)
        REFERENCES `catalog_products` (`catalog_product_id`) ON DELETE CASCADE
);
