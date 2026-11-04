-- 엑셀 영양제(supplements) 테스트 데이터 (9600번대 id, 테스트 트랜잭션이 끝나면 롤백)
INSERT INTO users (user_id, email, password_hash, nickname, share_intake_status, sensitive_consent, created_at, updated_at, last_active_at) VALUES
    (9601, 'supplement-test-9601@example.com', 'test-hash', '영양제테스트9601', TRUE, FALSE, NOW(), NOW(), NOW());

-- 9601: 1일 2회, 1회 비타민D 5㎍·아연 4mg → 하루 10㎍·8mg / 9602: 함량 정보 없음 / 9603: 검색 전용
INSERT INTO supplements (supplement_id, food_code, report_no, product_name, manufacturer, category_group, category, serving_size, servings_per_day, nutrient_summary) VALUES
    (9601, 'TEST-F-9601', 'TEST-R-9601', '테스트 비타민D 아연', '테스트제약', '복합', '복합제품(영양소)', '500mg', 2, '비타민 D 10㎍ · 아연 8mg'),
    (9602, 'TEST-F-9602', 'TEST-R-9602', '테스트 함량없음', '테스트제약', '기능성 원료', '엠에스엠', '800mg', 1, NULL),
    (9603, 'TEST-F-9603', 'TEST-R-9603', '테스트 오메가', '다른회사', '영양성분', 'EPA 및 DHA 함유 유지', '1000mg', 1, '오메가3 600mg');

INSERT INTO supplement_nutrients (supplement_nutrient_id, supplement_id, nutrient_code, amount_per_serving, amount_per_day, unit) VALUES
    (9601, 9601, 'VITAMIN_D', 5, 10, 'ug'),
    (9602, 9601, 'ZINC', 4, 8, 'mg'),
    (9603, 9603, 'OMEGA3', 600, 600, 'mg');

-- 9601: 엑셀 영양제 9601을 아침·저녁 2번 / 9602: 함량 정보 없는 엑셀 영양제
INSERT INTO user_products (user_product_id, user_id, catalog_product_id, supplement_id, product_kind, manual_product_name, registered_via, start_date, is_active, created_at) VALUES
    (9601, 9601, NULL, 9601, 'SUPPLEMENT', NULL, 'SEARCH', '2026-01-01', TRUE, NOW()),
    (9602, 9601, NULL, 9602, 'SUPPLEMENT', NULL, 'SEARCH', '2026-01-01', TRUE, NOW());

INSERT INTO intake_schedules (schedule_id, user_product_id, meal_slot, meal_relation, remind_at, is_enabled, created_at) VALUES
    (9601, 9601, 'BREAKFAST', 'AFTER', '08:00:00', TRUE, '2026-10-01 00:00:00'),
    (9602, 9601, 'DINNER',    'AFTER', '19:00:00', TRUE, '2026-10-01 00:00:00'),
    (9603, 9602, 'LUNCH',     'AFTER', '12:30:00', TRUE, '2026-10-01 00:00:00');
