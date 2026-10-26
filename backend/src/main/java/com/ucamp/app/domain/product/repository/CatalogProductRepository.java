package com.ucamp.app.domain.product.repository;

import com.ucamp.app.domain.product.entity.CatalogProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogProductRepository extends JpaRepository<CatalogProduct, Long> {
    // 제품명 또는 제조사에 검색어가 들어간 영양제 (제품명 가나다순)
    List<CatalogProduct> findByProductNameContainingOrManufacturerContainingOrderByProductNameAsc(
            String productName, String manufacturer);

    // 검색어가 없을 때 전체 목록 (제품명 가나다순)
    List<CatalogProduct> findAllByOrderByProductNameAsc();
}
