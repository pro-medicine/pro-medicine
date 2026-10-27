package com.ucamp.app.domain.recommendation.repository;

import com.ucamp.app.domain.recommendation.entity.AiRecommendationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiRecommendationItemRepository extends JpaRepository<AiRecommendationItem, Long> {
    List<AiRecommendationItem> findByRecommendation_RecommendationIdOrderBySortOrderAscRecommendationItemIdAsc(Long recommendationId);
}
