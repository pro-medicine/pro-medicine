-- 리뷰 제품 추천 테스트 데이터 (예시 데이터. 실제 제품·약사 리뷰가 아니다). 먼저 sql/schema-review-products.sql 실행
-- 다른 테스트 데이터와 겹치지 않도록 9300번대 id. 테스트 트랜잭션 안에서 실행되어 테스트가 끝나면 롤백된다
--
-- MAGNESIUM 후보 (base_score 순): 9307 차단(5.0) · 9308 비활성(4.0) → 조회에서 빠짐
--   9301 NOW 3.0 / 9302 NOW 2.8 / 9303 NOW 2.6 (같은 브랜드 3번째 → 브랜드 최대 2개로 빠짐)
--   9305 Solgar 2.0 · 9304 Doctor's Best 2.0 (동점, 추천 약사 수 같음 → 마지막 추천일이 최신인 9305 먼저)
--   9309 Jarrow 1.5 (OTHER 성분) / 9310 1.4 (라벨 성분 없음 → 제외) / 9306 Thorne 1.2 (MAGNESIUM + VITAMIN_B6 복합, 커버리지 대상)
-- 9313 Nature Made: 라벨에 MAGNESIUM이 있지만 리뷰 점수는 VITAMIN_B6뿐 → MAGNESIUM 카드에 나오면 안 됨
-- VITAMIN_D 후보: 9311 D3 + K2 (VITAMIN_K 같이 듦 → 와파린이면 제외) / 9312 D3만
-- OMEGA3 후보: 9314 오메가3 + 루테인 (임신·수유면 같이 든 루테인 때문에 제외, V2 회차 임신 답 확인용) / 9315 오메가3만
INSERT INTO catalog_products (catalog_product_id, product_name, name_ko, brand, data_source, external_id, image_url, is_active, blocked_kr, created_at) VALUES
    (9301, 'Magnesium Glycinate', '나우 마그네슘 글리시네이트', 'NOW', 'SAMPLE', 'T-9301', NULL, TRUE, FALSE, NOW()),
    (9302, 'Magnesium Citrate', NULL, 'NOW', 'SAMPLE', 'T-9302', NULL, TRUE, FALSE, NOW()),
    (9303, 'Magnesium Taurate', NULL, 'NOW', 'SAMPLE', 'T-9303', NULL, TRUE, FALSE, NOW()),
    (9304, 'High Absorption Magnesium', NULL, 'Doctor''s Best', 'SAMPLE', 'T-9304', NULL, TRUE, FALSE, NOW()),
    (9305, 'Chelated Magnesium', '솔가 킬레이트 마그네슘', 'Solgar', 'SAMPLE', 'T-9305', NULL, TRUE, FALSE, NOW()),
    (9306, 'Magnesium Bisglycinate + B6', NULL, 'Thorne', 'SAMPLE', 'T-9306', 'https://example.com/9306.png', TRUE, FALSE, NOW()),
    (9307, 'Magnesium Sleep Blend', NULL, 'Blocked Labs', 'SAMPLE', 'T-9307', NULL, TRUE, TRUE, NOW()),
    (9308, 'Discontinued Magnesium', NULL, 'Old Brand', 'SAMPLE', 'T-9308', NULL, FALSE, FALSE, NOW()),
    (9309, 'Magnesium + Ashwagandha', NULL, 'Jarrow', 'SAMPLE', 'T-9309', NULL, TRUE, FALSE, NOW()),
    (9310, 'Mystery Magnesium', NULL, 'Unknown Labs', 'SAMPLE', 'T-9310', NULL, TRUE, FALSE, NOW()),
    (9311, 'Vitamin D3 + K2', NULL, 'Sports Research', 'SAMPLE', 'T-9311', NULL, TRUE, FALSE, NOW()),
    (9312, 'Vitamin D3 2000IU', NULL, 'NatureWise', 'SAMPLE', 'T-9312', NULL, TRUE, FALSE, NOW()),
    (9313, 'B6 + Magnesium', NULL, 'Nature Made', 'SAMPLE', 'T-9313', NULL, TRUE, FALSE, NOW()),
    (9314, 'Omega-3 + Lutein', NULL, 'Eye Labs', 'SAMPLE', 'T-9314', NULL, TRUE, FALSE, NOW()),
    (9315, 'Ultra Omega-3', NULL, 'Nordic', 'SAMPLE', 'T-9315', NULL, TRUE, FALSE, NOW());

INSERT INTO product_ingredients (catalog_product_id, ingredient_code, ingredient_name, amount_per_day, unit, source) VALUES
    (9301, 'MAGNESIUM', 'Magnesium', 200, 'mg', 'MANUAL'),
    (9302, 'MAGNESIUM', 'Magnesium', 200, 'mg', 'MANUAL'),
    (9303, 'MAGNESIUM', 'Magnesium', 125, 'mg', 'MANUAL'),
    (9304, 'MAGNESIUM', 'Magnesium', 100, 'mg', 'MANUAL'),
    (9305, 'MAGNESIUM', 'Magnesium', 200, 'mg', 'MANUAL'),
    (9306, 'MAGNESIUM', 'Magnesium', 200, 'mg', 'MANUAL'),
    (9306, 'VITAMIN_B6', 'Vitamin B6', 10, 'mg', 'MANUAL'),
    (9307, 'MAGNESIUM', 'Magnesium', 200, 'mg', 'MANUAL'),
    (9307, 'OTHER', 'Melatonin', 3, 'mg', 'MANUAL'),
    (9308, 'MAGNESIUM', 'Magnesium', 200, 'mg', 'MANUAL'),
    (9309, 'MAGNESIUM', 'Magnesium', 150, 'mg', 'MANUAL'),
    (9309, 'OTHER', 'Ashwagandha', NULL, NULL, 'MANUAL'),
    (9311, 'VITAMIN_D', 'Vitamin D3', 125, 'mcg', 'MANUAL'),
    (9311, 'VITAMIN_K', 'Vitamin K2', 100, 'mcg', 'MANUAL'),
    (9312, 'VITAMIN_D', 'Vitamin D3', 50, 'mcg', 'MANUAL'),
    (9313, 'VITAMIN_B6', 'Vitamin B6', 25, 'mg', 'MANUAL'),
    (9313, 'MAGNESIUM', 'Magnesium', 50, 'mg', 'MANUAL'),
    (9314, 'OMEGA3', 'Fish Oil', 1000, 'mg', 'MANUAL'),
    (9314, 'LUTEIN', 'Lutein', 10, 'mg', 'MANUAL'),
    (9315, 'OMEGA3', 'Fish Oil', 1000, 'mg', 'MANUAL');

INSERT INTO review_product_scores (ingredient_code, catalog_product_id, base_score, recommend_channels, not_recommend_channels, last_published_at, common_cautions, mention_ids, computed_at) VALUES
    ('MAGNESIUM', 9301, 3.000, 3, 0, '2026-09-01', '["알약이 클 수 있어요"]', '[9301]', NOW()),
    ('MAGNESIUM', 9302, 2.800, 2, 0, '2026-08-01', NULL, '[9302]', NOW()),
    ('MAGNESIUM', 9303, 2.600, 2, 0, '2026-08-01', NULL, '[9303]', NOW()),
    ('MAGNESIUM', 9304, 2.000, 2, 0, '2026-08-01', NULL, '[9304]', NOW()),
    ('MAGNESIUM', 9305, 2.000, 2, 0, '2026-09-15', '[{"category": "SIDE_EFFECT", "text": "공복에 먹으면 속이 불편할 수 있어요"}]', '[9305]', NOW()),
    ('MAGNESIUM', 9306, 1.200, 1, 0, '2026-07-01', NULL, '[9306]', NOW()),
    ('MAGNESIUM', 9307, 5.000, 4, 0, '2026-10-01', NULL, '[9307]', NOW()),
    ('MAGNESIUM', 9308, 4.000, 3, 0, '2026-10-01', NULL, '[9308]', NOW()),
    ('MAGNESIUM', 9309, 1.500, 1, 0, '2026-06-01', NULL, '[9309]', NOW()),
    ('MAGNESIUM', 9310, 1.400, 1, 0, '2026-06-01', NULL, '[9310]', NOW()),
    ('VITAMIN_D', 9311, 3.000, 2, 0, '2026-09-01', NULL, '[9311]', NOW()),
    ('VITAMIN_D', 9312, 2.000, 1, 0, '2026-09-01', NULL, '[9312]', NOW()),
    ('VITAMIN_B6', 9313, 9.000, 3, 0, '2026-09-01', NULL, '[9313]', NOW()),
    ('OMEGA3', 9314, 3.000, 2, 0, '2026-09-01', NULL, '[9314]', NOW()),
    ('OMEGA3', 9315, 2.000, 2, 0, '2026-09-01', NULL, '[9315]', NOW());

-- 언급 (V3 요청 경로에서는 읽지 않는다. 스키마 확인용)
INSERT INTO review_mentions (video_id, mention_no, channel_id, published_at, catalog_product_id, resolve_status, product_name_ko, brand_en, ingredients, stance, is_sponsored, purpose_tags, cautions, context, embedding_model, start_sec, evidence_quote, prompt_version) VALUES
    ('TESTVID0001', 1, 'TESTCH01', '2026-09-01', 9301, 'RESOLVED', '나우 마그네슘 글리시네이트', 'NOW', '["MAGNESIUM"]', 'RECOMMEND', FALSE, '["SLEEP"]', '[{"category": "FORM", "text": "알약이 클 수 있어요"}]', '예시 데이터: 잠들기 어려운 사람에게 추천', 'text-embedding-3-small', 120, '예시 인용', 'review-extract-v3'),
    ('TESTVID0002', 1, 'TESTCH02', '2026-08-15', 9301, 'RESOLVED', '나우 마그네슘', 'NOW', '["MAGNESIUM"]', 'RECOMMEND', FALSE, NULL, '[{"category": "FORM", "text": "알약이 클 수 있어요"}]', NULL, NULL, 300, NULL, 'review-extract-v3'),
    ('TESTVID0003', 2, 'TESTCH03', '2026-07-01', NULL, 'PENDING_REVIEW', '예시 국내 유산균', NULL, '["PROBIOTICS"]', 'RECOMMEND', TRUE, NULL, NULL, NULL, NULL, NULL, NULL, 'review-extract-v3');
