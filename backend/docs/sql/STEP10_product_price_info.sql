-- STEP 10: 추천 제품 카드에 "용량 얼마에 가격 얼마 (출처)"를 보여주기 위한 컬럼
-- 영향: 리뷰 제품 추천(제품 추천 보기), 대화형 추천의 제품 카드. 다른 기능은 이 컬럼을 읽지 않는다.
-- 기존 price(원)는 그대로 쓰고, 그 가격의 용량·출처·확인 날짜만 더한다. package_size(내 영양제 카드용)와는 따로 둔다.
-- 실행: 이미 만들어진 DB(로컬·서버)에서 한 번만. 새로 만드는 DB는 backend/schema.sql에 이미 들어 있다.
-- 두 번 실행하면 "Duplicate column" 오류가 나는데, 이미 적용됐다는 뜻이라 무시하면 된다.
USE yakssok;

ALTER TABLE `catalog_products`
    ADD COLUMN `price_package`    VARCHAR(100)  NULL COMMENT 'price의 용량 (예: 60캡슐)',
    ADD COLUMN `price_source`     VARCHAR(50)   NULL COMMENT 'price 출처 이름 (예: 아이허브)',
    ADD COLUMN `price_url`        VARCHAR(1000) NULL COMMENT 'price 출처 주소 (https)',
    ADD COLUMN `price_checked_at` DATE          NULL COMMENT 'price 확인 날짜';
