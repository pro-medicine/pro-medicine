package com.ucamp.app.domain.recommendation.repository;

import com.ucamp.app.domain.review.dto.ReviewProductResponse;
import com.ucamp.app.domain.review.repository.PriceColumns;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V7.1 추천 기록 상세: 저장된 제품 id → 지금 보여줄 제품 정보 (읽기 전용, 다른 팀원 테이블 구조는 바꾸지 않음).
 * 판매 중단·국내 반입 차단(is_active = FALSE, blocked_kr = TRUE) 제품은 결과에서 빠진다 (V3 후보 조회와 같은 조건).
 * 공통 주의점은 리뷰 점수(review_product_scores)의 지금 값
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RecommendationProductQueryRepository {
    private static final String PRODUCTS_SQL = """
            SELECT p.catalog_product_id, COALESCE(p.name_ko, p.product_name) AS display_name, p.brand, p.image_url, %s
            FROM catalog_products p
            WHERE p.catalog_product_id IN (:ids) AND p.is_active = TRUE AND p.blocked_kr = FALSE
            """.formatted(PriceColumns.select("p."));
    private static final String CAUTIONS_SQL = """
            SELECT catalog_product_id, ingredient_code, common_cautions
            FROM review_product_scores
            WHERE catalog_product_id IN (:ids)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** 제품 표시 정보 (이름·브랜드·사진·참고 가격) */
    public record Display(long catalogProductId, String productName, String brand, String imageUrl,
                          ReviewProductResponse.Price price) {
    }

    /** 지금 보여줄 수 있는 제품만 id → 표시 정보 */
    public Map<Long, Display> findDisplayable(Collection<Long> ids) {
        Map<Long, Display> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        jdbcTemplate.query(PRODUCTS_SQL, new MapSqlParameterSource("ids", Set.copyOf(ids)), rs -> {
            long id = rs.getLong("catalog_product_id");
            out.put(id, new Display(id, rs.getString("display_name"), rs.getString("brand"), rs.getString("image_url"),
                    PriceColumns.read(rs)));
        });
        return out;
    }

    /** "제품id:성분코드" → 공통 주의점 */
    public Map<String, List<String>> findCautions(Collection<Long> ids) {
        Map<String, List<String>> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        jdbcTemplate.query(CAUTIONS_SQL, new MapSqlParameterSource("ids", Set.copyOf(ids)), rs -> {
            out.put(key(rs.getLong("catalog_product_id"), rs.getString("ingredient_code")),
                    cautions(rs.getString("common_cautions")));
        });
        return out;
    }

    public static String key(long catalogProductId, String ingredient) {
        return catalogProductId + ":" + ingredient;
    }

    // ReviewProductQueryRepository와 같은 형식: ["..."] 또는 [{"text": "..."}]. 읽을 수 없으면 빈 목록
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
