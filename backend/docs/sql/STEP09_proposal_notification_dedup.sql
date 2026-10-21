-- [제안] STEP 09: 같은 날 같은 알림이 두 번 생성되지 않게 notifications.dedup_key에 유니크 제약 추가
-- notification 도메인은 응진 담당이라 적용하지 않았다. 협의 후 적용 여부를 정한다.
-- dedup_key 형식 제안: {user_id}:{notification_type}:{yyyyMMdd}  (예: 12:NUTRIENT_REMINDER:20261021)
-- dedup_key는 NULL을 허용하므로(기존 알림) MySQL 유니크 인덱스에서 NULL끼리는 중복으로 보지 않는다.
-- 적용 전 중복 확인:
--   SELECT dedup_key, COUNT(*) FROM notifications WHERE dedup_key IS NOT NULL GROUP BY dedup_key HAVING COUNT(*) > 1;
ALTER TABLE `notifications` ADD CONSTRAINT `UK_NOTIFICATIONS_DEDUP_KEY` UNIQUE (`dedup_key`);
-- 발송 배치에서는 INSERT ... 실패(중복 키)를 "이미 보냄"으로 처리하면 된다.
