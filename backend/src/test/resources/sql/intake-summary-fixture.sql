-- IntakeSummaryServiceTest 전용 테스트 데이터
-- 예시 데이터(schema.sql)와 겹치지 않도록 9500번대 id와 별도 이메일·닉네임을 사용한다.
-- 테스트 트랜잭션 안에서 실행되어 테스트가 끝나면 롤백된다.
-- 기준일: 2026-10-29 (목). 이번 주 = 10/26(월) ~ 10/29(목)

INSERT INTO users (user_id, email, password_hash, nickname, share_intake_status, sensitive_consent, created_at, updated_at, last_active_at) VALUES
    (9501, 'summary-test-9501@example.com', 'test-hash', '요약테스트9501', FALSE, FALSE, NOW(), NOW(), NOW()),
    (9502, 'summary-test-9502@example.com', 'test-hash', '요약테스트9502', FALSE, FALSE, NOW(), NOW(), NOW());

-- 9504는 복용을 중단한 제품 (is_active = FALSE)
INSERT INTO user_products (user_product_id, user_id, catalog_product_id, product_kind, manual_product_name, registered_via, start_date, is_active, created_at) VALUES
    (9501, 9501, NULL, 'SUPPLEMENT', '테스트 종합비타민', 'MANUAL', '2026-01-01', TRUE,  NOW()),
    (9502, 9501, NULL, 'SUPPLEMENT', '테스트 오메가3',    'MANUAL', '2026-01-01', TRUE,  NOW()),
    (9503, 9501, NULL, 'SUPPLEMENT', '테스트 유산균',     'MANUAL', '2026-01-01', TRUE,  NOW()),
    (9504, 9501, NULL, 'SUPPLEMENT', '테스트 끊은 영양제', 'MANUAL', '2026-01-01', FALSE, NOW());

-- 9501: 아침 08:30 (종합비타민)
-- 9502: 점심 12:30:00 (오메가3)
-- 9503: 점심 12:30:30 (유산균) → 9502와 같은 분이라 다음 알림에서 하나로 묶여야 함
-- 9504: 저녁 19:00 (종합비타민)
-- 9505: 점심 11:00 (끊은 영양제) → 다음 알림에서 제외되어야 함
INSERT INTO intake_schedules (schedule_id, user_product_id, meal_slot, meal_relation, remind_at, is_enabled, created_at) VALUES
    (9501, 9501, 'BREAKFAST', 'AFTER', '08:30:00', TRUE, NOW()),
    (9502, 9502, 'LUNCH',     'AFTER', '12:30:00', TRUE, NOW()),
    (9503, 9503, 'LUNCH',     'AFTER', '12:30:30', TRUE, NOW()),
    (9504, 9501, 'DINNER',    'AFTER', '19:00:00', TRUE, NOW()),
    (9505, 9504, 'LUNCH',     'AFTER', '11:00:00', TRUE, NOW());

-- 이번 주 복용 체크 4건 중 제시간 2건 (50%)
--   10/26 아침(08:30) 08:40 체크 → 제시간
--   10/26 점심(12:30) 14:00 체크 → 늦음 (13:30 지남)
--   10/27 아침(08:30) 07:50 체크 → 미리 먹음 = 제시간
--   10/28 아침(08:30) 다음 날 09:00 체크 → 늦음
-- 계산에서 빠져야 하는 기록
--   10/25(지난주 일요일) 아침 08:31 체크 → 이번 주가 아님
--   10/27 점심 SKIPPED → 복용 체크가 아님
INSERT INTO intake_records (intake_record_id, schedule_id, intake_date, status, completed_at, is_shared) VALUES
    (9501, 9501, '2026-10-26', 'TAKEN',   '2026-10-26 08:40:00', FALSE),
    (9502, 9502, '2026-10-26', 'TAKEN',   '2026-10-26 14:00:00', FALSE),
    (9503, 9501, '2026-10-27', 'TAKEN',   '2026-10-27 07:50:00', FALSE),
    (9504, 9501, '2026-10-28', 'TAKEN',   '2026-10-29 09:00:00', FALSE),
    (9505, 9501, '2026-10-25', 'TAKEN',   '2026-10-25 08:31:00', FALSE),
    (9506, 9502, '2026-10-27', 'SKIPPED', NULL,                  FALSE);
