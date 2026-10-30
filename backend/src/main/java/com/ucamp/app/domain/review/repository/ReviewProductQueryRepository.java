package com.ucamp.app.domain.review.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 리뷰 제품 추천 조회 (읽기 전용). 테이블은 STEP17, 데이터는 월 1회 SQL import로만 바뀐다 → 엔티티 대신 JdbcTemplate.
 * 자막 원문·evidence_quote·context(review_mentions)는 읽지 않는다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ReviewProductQueryRepository {
    private static final String CANDIDATES_SQL = """
            SELECT s.catalog_product_id, COALESCE(p.name_ko, p.product_name) AS display_name, p.brand, p.image_url,
                   s.base_score, s.recommend_channels, s.not_recommend_channels, s.last_published_at, s.common_cautions
            FROM review_product_scores s
            JOIN catalog_products p ON p.catalog_product_id = s.catalog_product_id
            WHERE s.ingredient_code = ? AND p.is_active = TRUE AND p.blocked_kr = FALSE
            ORDER BY s.base_score DESC, s.catalog_product_id ASC
            LIMIT ?
            """;
    private static final String INGREDIENTS_SQL = """
            SELECT catalog_product_id, ingredient_code
            FROM product_ingredients
            WHERE catalog_product_id IN (:ids)
            ORDER BY catalog_product_id, product_ingredient_id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final ObjectMapper objectMapper;

    /** 성분 x 제품 인기 점수 한 행 + 제품 표시 정보 */
    public record Candidate(long catalogProductId, String productName, String brand, String imageUrl,
                            BigDecimal baseScore, int recommendChannels, int notRecommendChannels,
                            LocalDate lastPublishedAt, List<String> commonCautions) {
    }

    /** 리뷰에서 ingredientCode 제품으로 추천된 활성·국내 반입 가능 제품을 인기 점수 순으로 limit개 */
    public List<Candidate> findCandidates(String ingredientCode, int limit) {
        return jdbcTemplate.query(CANDIDATES_SQL, (rs, n) -> {
            Date last = rs.getDate("last_published_at");
            return new Candidate(rs.getLong("catalog_product_id"), rs.getString("display_name"), rs.getString("brand"),
                    rs.getString("image_url"), rs.getBigDecimal("base_score"), rs.getInt("recommend_channels"),
                    rs.getInt("not_recommend_channels"), last == null ? null : last.toLocalDate(),
                    cautions(rs.getString("common_cautions")));
        }, ingredientCode, limit);
    }

    /** 제품별 라벨 성분 코드 (한 번에 조회). 성분 행이 없는 제품은 결과 맵에 없다 */
    public Map<Long, List<String>> findIngredients(Collection<Long> productIds) {
        Map<Long, List<String>> out = new LinkedHashMap<>();
        if (productIds.isEmpty()) {
            return out;
        }
        namedJdbcTemplate.query(INGREDIENTS_SQL, new MapSqlParameterSource("ids", productIds), rs -> {
            out.computeIfAbsent(rs.getLong("catalog_product_id"), k -> new ArrayList<>())
                    .add(rs.getString("ingredient_code"));
        });
        return out;
    }

    // common_cautions: ["알약이 커요"] 또는 [{"category": ..., "text": "알약이 커요"}]. 읽을 수 없으면 빈 목록
    private List<String> cautions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> out = new ArrayList<>();
            for (JsonNode node : objectMapper.readTree(json)) {
                String text = node.isString() ? node.asString() : node.path("text").asString(null);
                if (text != null && !text.isBlank()) {
                    out.add(text);
                }
            }
            return List.copyOf(out);
        } catch (RuntimeException e) {
            log.warn("review_product_scores.common_cautions parse failed: {}", e.getClass().getSimpleName());
            return List.of();
        }
    }
}
