package com.ucamp.app.domain.recommendation.repository;

import com.ucamp.app.domain.recommendation.entity.AiRecommendationProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiRecommendationProductRepository extends JpaRepository<AiRecommendationProduct, Long> {

    // 이미 저장한 추천이면 다시 저장하지 않는다
    boolean existsByItem_Recommendation_RecommendationId(Long recommendationId);

    // 추천 하나의 제품 순위 전부 (성분 카드 순서 → 순위)
    @Query("select p from AiRecommendationProduct p join fetch p.item i "
            + "where i.recommendation.recommendationId = :recommendationId "
            + "order by i.sortOrder, i.recommendationItemId, p.productRank")
    List<AiRecommendationProduct> findByRecommendationId(@Param("recommendationId") Long recommendationId);
}
