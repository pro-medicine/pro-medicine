-- 추천 제품 이미지·참고 가격 등록용 제품 목록 (scripts/product-images/README.md 1단계)
-- 영준님 추천 로직이 고를 수 있는 제품 = review_product_scores에 있는 활성·국내 반입 가능 제품
-- best_rank: 성분별 인기 점수 순위 중 가장 높은 순위 (1~3이면 화면에 자주 나옴 → 먼저 채우기)
-- 결과를 CSV(UTF-8)로 내보낸 뒤 image_file, price, image_source, price_* 칸을 채운다.
-- 이미 등록한 가격의 용량·출처·확인 날짜도 같이 내보낸다 → 그대로 다시 빌드해도 값이 지워지지 않는다.
-- data_source = DSLD 인 제품은 fetch-dsld-images.mjs가 external_id(DSLD 라벨 번호)로 사진을 자동으로 받는다.
-- only_missing = 1 로 바꾸면 이미지가 아직 없는 제품만 나온다 (추천 데이터가 새로 들어왔을 때)

SET @only_missing = 0;

SELECT p.catalog_product_id,
       COALESCE(p.name_ko, p.product_name) AS name,
       p.brand,
       p.data_source,
       p.external_id,
       GROUP_CONCAT(r.ingredient_code ORDER BY r.rank_in_ingredient SEPARATOR ' ') AS ingredients,
       MIN(r.rank_in_ingredient) AS best_rank,
       '' AS image_file,
       COALESCE(CAST(p.price AS CHAR), '') AS price,
       '' AS image_source,
       COALESCE(p.price_package, '') AS price_package,
       COALESCE(p.price_source, '') AS price_source,
       COALESCE(p.price_url, '') AS price_url,
       COALESCE(CAST(p.price_checked_at AS CHAR), '') AS price_checked_at
FROM (
    SELECT s.catalog_product_id, s.ingredient_code,
           ROW_NUMBER() OVER (PARTITION BY s.ingredient_code ORDER BY s.base_score DESC, s.catalog_product_id) AS rank_in_ingredient
    FROM review_product_scores s
    JOIN catalog_products cp ON cp.catalog_product_id = s.catalog_product_id
    WHERE cp.is_active = TRUE AND cp.blocked_kr = FALSE
) r
JOIN catalog_products p ON p.catalog_product_id = r.catalog_product_id
WHERE @only_missing = 0 OR p.image_url IS NULL
GROUP BY p.catalog_product_id, name, p.brand, p.data_source, p.external_id, p.price,
         p.price_package, p.price_source, p.price_url, p.price_checked_at
ORDER BY best_rank, p.catalog_product_id;
