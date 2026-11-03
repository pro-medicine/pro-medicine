package com.ucamp.app.domain.product.repository;

import com.ucamp.app.domain.product.entity.Supplement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplementRepository extends JpaRepository<Supplement, Long> {

    // 등록 폼 검색: 제품명 또는 제조사에 검색어가 들어간 제품 최대 20개 (기존 catalog_products 검색과 같은 방식)
    List<Supplement> findTop20ByProductNameContainingOrManufacturerContainingOrderByProductNameAsc(
            String productName, String manufacturer);
}
