package com.ucamp.app.domain.recommendation.repository;

import com.ucamp.app.domain.recommendation.entity.IngredientStandard;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientStandardRepository extends JpaRepository<IngredientStandard, Long> {
}
