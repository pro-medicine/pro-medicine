-- IntakeScheduleCompletionTest 전용 테스트 데이터
-- 예시 데이터(schema.sql)·다른 테스트 데이터와 겹치지 않도록 9100번대 id를 사용한다.
-- 테스트 트랜잭션 안에서 실행되어 테스트가 끝나면 롤백된다.

INSERT INTO users (user_id, email, password_hash, nickname, share_intake_status, sensitive_consent, created_at, updated_at, last_active_at) VALUES
                                                                                                                                (9101, 'completion-test-9101@example.com', 'test-hash', '완료테스트9101', TRUE, FALSE, NOW(), NOW(), NOW()),
                                                                                                                                (9102, 'completion-test-9102@example.com', 'test-hash', '완료테스트9102', TRUE, FALSE, NOW(), NOW(), NOW());

-- 9101: 복용 중 영양제 / 9102: 복용 중 약 / 9103: 복용 중단 / 9104: 복용 시작일이 미래
INSERT INTO user_products (user_product_id, user_id, catalog_product_id, product_kind, manual_product_name, registered_via, start_date, is_active, created_at) VALUES
                                                                                                                                                                   (9101, 9101, NULL, 'SUPPLEMENT', '완료테스트 비타민', 'MANUAL', '2026-01-01', TRUE,  NOW()),
                                                                                                                                                                   (9102, 9101, NULL, 'MEDICINE',   '완료테스트 약',     'MANUAL', '2026-01-01', TRUE,  NOW()),
                                                                                                                                                                   (9103, 9101, NULL, 'SUPPLEMENT', '완료테스트 중단',   'MANUAL', '2026-01-01', FALSE, NOW()),
                                                                                                                                                                   (9104, 9101, NULL, 'SUPPLEMENT', '완료테스트 미래',   'MANUAL', '2026-12-01', TRUE,  NOW());

-- 사용자 9101의 오늘 먹어야 하는 일정: 9101(아침), 9102(저녁, 약) → 2개
-- 9103: 알림 꺼짐 / 9104: 복용 중단 제품 / 9105: 시작일 전 제품 → 제외
INSERT INTO intake_schedules (schedule_id, user_product_id, meal_slot, meal_relation, remind_at, is_enabled, created_at) VALUES
                                                                                                                             (9101, 9101, 'BREAKFAST', 'AFTER', '08:00:00', TRUE,  NOW()),
                                                                                                                             (9102, 9102, 'DINNER',    'AFTER', '19:00:00', TRUE,  NOW()),
                                                                                                                             (9103, 9101, 'LUNCH',     'AFTER', '12:00:00', FALSE, NOW()),
                                                                                                                             (9104, 9103, 'LUNCH',     'AFTER', '12:00:00', TRUE,  NOW()),
                                                                                                                             (9105, 9104, 'LUNCH',     'AFTER', '12:00:00', TRUE,  NOW());

-- 2026-10-23에 일정 9101만 복용 완료
INSERT INTO intake_records (intake_record_id, schedule_id, intake_date, status, completed_at, is_shared) VALUES
    (9101, 9101, '2026-10-23', 'TAKEN', '2026-10-23 08:05:00', FALSE);
