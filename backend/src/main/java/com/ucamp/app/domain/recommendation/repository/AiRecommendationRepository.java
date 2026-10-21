package com.ucamp.app.domain.recommendation.repository;

import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRecommendationRepository extends JpaRepository<AiRecommendation, Long> {
}
