-- 리뷰 제품 추천 테스트용 H2 DDL (운영 MySQL DDL은 backend/docs/sql/STEP17_review_product_data.sql)
-- 새 테이블은 엔티티가 없어 ddl-auto가 만들지 않고, catalog_products는 엔티티 컬럼만 만들어지므로 여기서 맞춘다.
-- 테스트 컨텍스트가 바뀌어 Hibernate가 catalog_products를 다시 만들 수 있도록 FK는 걸지 않는다.
-- H2의 JSON 타입은 문자열 INSERT를 JSON 문자열로 저장하므로 JSON 컬럼은 VARCHAR로 둔다. 다시 실행해도 안전하다
ALTER TABLE catalog_products ADD COLUMN IF NOT EXISTS name_ko VARCHAR(255);
ALTER TABLE catalog_products ADD COLUMN IF NOT EXISTS brand VARCHAR(255);
ALTER TABLE catalog_products ADD COLUMN IF NOT EXISTS data_source VARCHAR(20) DEFAULT 'SAMPLE' NOT NULL;
ALTER TABLE catalog_products ADD COLUMN IF NOT EXISTS external_id VARCHAR(64);
ALTER TABLE catalog_products ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE NOT NULL;
ALTER TABLE catalog_products ADD COLUMN IF NOT EXISTS blocked_kr BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE catalog_products ADD COLUMN IF NOT EXISTS image_key VARCHAR(500);
ALTER TABLE catalog_products ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS product_ingredients (
    product_ingredient_id BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    catalog_product_id    BIGINT        NOT NULL,
    ingredient_code       VARCHAR(30)   NOT NULL,
    ingredient_name       VARCHAR(100)  NOT NULL,
    amount_per_day        DECIMAL(12,3),
    unit                  VARCHAR(20),
    source                VARCHAR(20),
    CONSTRAINT uk_product_ingredients UNIQUE (catalog_product_id, ingredient_code, ingredient_name)
);

CREATE TABLE IF NOT EXISTS review_mentions (
    mention_id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    video_id           VARCHAR(20)  NOT NULL,
    mention_no         INT          NOT NULL,
    channel_id         VARCHAR(30)  NOT NULL,
    published_at       DATE,
    catalog_product_id BIGINT,
    resolve_status     VARCHAR(20)  NOT NULL,
    product_name_ko    VARCHAR(255),
    brand_en           VARCHAR(255),
    ingredients        VARCHAR(1000),
    stance             VARCHAR(15)  NOT NULL,
    is_sponsored       BOOLEAN      DEFAULT FALSE NOT NULL,
    purpose_tags       VARCHAR(1000),
    cautions           VARCHAR(4000),
    context            VARCHAR(400),
    embedding          BLOB,
    embedding_model    VARCHAR(80),
    start_sec          INT,
    evidence_quote     CLOB,
    prompt_version     VARCHAR(30),
    CONSTRAINT uk_review_mentions_video UNIQUE (video_id, mention_no)
);

CREATE TABLE IF NOT EXISTS review_product_scores (
    ingredient_code        VARCHAR(30)  NOT NULL,
    catalog_product_id     BIGINT       NOT NULL,
    base_score             DECIMAL(8,3) NOT NULL,
    recommend_channels     INT          DEFAULT 0 NOT NULL,
    not_recommend_channels INT          DEFAULT 0 NOT NULL,
    last_published_at      DATE,
    common_cautions        VARCHAR(4000),
    mention_ids            VARCHAR(1000),
    computed_at            TIMESTAMP,
    PRIMARY KEY (ingredient_code, catalog_product_id)
);
CREATE INDEX IF NOT EXISTS idx_review_product_scores_rank ON review_product_scores (ingredient_code, base_score DESC);
