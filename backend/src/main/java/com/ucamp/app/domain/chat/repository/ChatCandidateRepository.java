package com.ucamp.app.domain.chat.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 트랙 1 후보 제품 조회 (읽기 전용, V3 테이블). 활성·국내 반입 가능 조건은 V3 ReviewProductQueryRepository.findCandidates와 같다.
 * 라벨 성분은 V3 findIngredients를 그대로 쓴다
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ChatCandidateRepository {
    private static final String PRODUCTS_SQL = """
            SELECT catalog_product_id, COALESCE(name_ko, product_name) AS display_name, brand, image_url
            FROM catalog_products
            WHERE catalog_product_id IN (:ids) AND is_active = TRUE AND blocked_kr = FALSE
            """;
    private static final String SCORES_SQL = """
            SELECT ingredient_code, catalog_product_id, base_score, common_cautions
            FROM review_product_scores
            WHERE catalog_product_id IN (:ids)
            """;

    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final ObjectMapper objectMapper;

    public record Product(long catalogProductId, String productName, String brand, String imageUrl) {
    }

    /** V3 성분 x 제품 인기 점수 (상황 무관). 행이 없으면 맵에 없다 → 0으로 본다 */
    public record BaseScore(BigDecimal baseScore, List<String> commonCautions) {
    }

    /** 활성·국내 반입 가능한 제품만 (차단·비활성 제품은 결과 맵에 없다) */
    public Map<Long, Product> findActiveProducts(Collection<Long> ids) {
        Map<Long, Product> out = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        namedJdbcTemplate.query(PRODUCTS_SQL, new MapSqlParameterSource("ids", ids), rs -> {
            long id = rs.getLong("catalog_product_id");
            out.put(id, new Product(id, rs.getString("display_name"), rs.getString("brand"), rs.getString("image_url")));
        });
        return out;
    }

    /** "성분코드:제품id" → 점수 */
    public Map<String, BaseScore> findBaseScores(Collection<Long> ids) {
        Map<String, BaseScore> out = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        namedJdbcTemplate.query(SCORES_SQL, new MapSqlParameterSource("ids", ids), rs -> {
            out.put(key(rs.getString("ingredient_code"), rs.getLong("catalog_product_id")),
                    new BaseScore(rs.getBigDecimal("base_score"), cautions(rs.getString("common_cautions"))));
        });
        return out;
    }

    public static String key(String ingredient, long productId) {
        return ingredient + ":" + productId;
    }

    // V3와 같은 형식: ["알약이 커요"] 또는 [{"category": ..., "text": "알약이 커요"}]
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
