package com.ucamp.app.domain.recommendation.repository;

import com.ucamp.app.domain.recommendation.entity.RecommendationRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendationRuleRepository extends JpaRepository<RecommendationRule, String> {
    List<RecommendationRule> findByActiveTrueOrderByRuleIdAsc();
}
