-- TodayNutritionServiceTest 전용 테스트 데이터 (9200번대 id, 테스트 트랜잭션이 끝나면 롤백)
INSERT INTO users (user_id, email, password_hash, nickname, share_intake_status, sensitive_consent, created_at, updated_at, last_active_at) VALUES
    (9201, 'nutrition-test-9201@example.com', 'test-hash', '영양테스트9201', TRUE, FALSE, NOW(), NOW(), NOW());

-- 9201: 비타민C·D·아연 (식약처 성분 요약) / 9202: EPA+DHA 합쳐서 오메가3, 루테인은 게이지에 없음
INSERT INTO catalog_products (catalog_product_id, product_name, ingredient_summary, created_at) VALUES
    (9201, '영양테스트 멀티비타민', '비타민 C 100mg, 비타민 D 10㎍, 아연 8.5mg', NOW()),
    (9202, '영양테스트 오메가3', 'EPA 300mg, DHA 200mg, 루테인 20mg', NOW());

-- 9201: 하루 2번(아침·저녁) 먹는 멀티비타민 / 9202: 오메가3 / 9203: 직접 입력(함량 모름) / 9204: 처방약
INSERT INTO user_products (user_product_id, user_id, catalog_product_id, product_kind, manual_product_name, registered_via, start_date, is_active, created_at) VALUES
    (9201, 9201, 9201, 'SUPPLEMENT', NULL, 'SEARCH', '2026-01-01', TRUE, NOW()),
    (9202, 9201, 9202, 'SUPPLEMENT', NULL, 'SEARCH', '2026-01-01', TRUE, NOW()),
    (9203, 9201, NULL, 'SUPPLEMENT', '영양테스트 직접입력', 'MANUAL', '2026-01-01', TRUE, NOW()),
    (9204, 9201, NULL, 'MEDICINE', '영양테스트 처방약', 'MANUAL', '2026-01-01', TRUE, NOW());

INSERT INTO intake_schedules (schedule_id, user_product_id, meal_slot, meal_relation, remind_at, is_enabled, created_at) VALUES
    (9201, 9201, 'BREAKFAST', 'AFTER', '08:00:00', TRUE, '2026-10-01 00:00:00'),
    (9202, 9201, 'DINNER',    'AFTER', '19:00:00', TRUE, '2026-10-01 00:00:00'),
    (9203, 9202, 'LUNCH',     'AFTER', '12:30:00', TRUE, '2026-10-01 00:00:00'),
    (9204, 9203, 'LUNCH',     'AFTER', '12:30:00', TRUE, '2026-10-01 00:00:00'),
    (9205, 9204, 'DINNER',    'AFTER', '19:00:00', TRUE, '2026-10-01 00:00:00');
