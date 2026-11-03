package com.ucamp.app.domain.recommendation.repository;

import com.ucamp.app.domain.recommendation.entity.AiRecommendationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AiRecommendationItemRepository extends JpaRepository<AiRecommendationItem, Long> {
    List<AiRecommendationItem> findByRecommendation_RecommendationIdOrderBySortOrderAscRecommendationItemIdAsc(Long recommendationId);

    // V7.1 추천 기록 목록: 여러 추천의 제외되지 않은 성분 (추천별 순서)
    @Query("select i from AiRecommendationItem i where i.recommendation.recommendationId in :ids and i.excluded = false "
            + "order by i.recommendation.recommendationId, i.sortOrder, i.recommendationItemId")
    List<AiRecommendationItem> findIncludedByRecommendationIds(@Param("ids") Collection<Long> ids);
}
