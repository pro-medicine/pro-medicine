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

    //[등록 폼] 위 검색과 조건은 같고 20개만 (식약처 데이터가 들어오면 "비타민"만 쳐도 수백개)
    List<CatalogProduct> findTop20ByProductNameContainingOrManufacturerContainingOrderByProductNameAsc(
            String productName, String manufacturer);

    List<CatalogProduct> findByMfdsItemNoIn(List<String> mfdsItemNos);

}
