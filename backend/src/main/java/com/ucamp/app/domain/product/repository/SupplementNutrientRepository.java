package com.ucamp.app.domain.product.repository;

import com.ucamp.app.domain.product.entity.SupplementNutrient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SupplementNutrientRepository extends JpaRepository<SupplementNutrient, Long> {

    // 여러 영양제의 성분 함량을 한 번에 조회 (영양제마다 따로 조회하지 않게)
    List<SupplementNutrient> findBySupplementSupplementIdIn(Collection<Long> supplementIds);
}
