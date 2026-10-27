package com.ucamp.app.domain.product.repository;

import com.ucamp.app.domain.product.entity.UserProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserProductRepository extends JpaRepository<UserProduct, Long> {

    /**
     * 마이페이지 "내가 먹는 영양제" 목록용
     * - catalogProduct 를 같이 가져와서 카드마다 추가 쿼리 나가는 것(N+1) 방지
     * - left join fetch: 직접 입력 제품(catalog_product_id = null)도 빠지지 않게
     */
    @Query("""
            select up
            from UserProduct up
            left join fetch up.catalogProduct
            where up.user.id = :userId
              and up.isActive = true
            order by up.createdAt asc, up.userProductId asc
            """)
    List<UserProduct> findActiveWithCatalogByUserId(@Param("userId") Long userId);

    // 이 유저가 이 제품을 이미 복용 중으로 등록했는지 (중복 등록 확인)
    boolean existsByUserIdAndCatalogProductCatalogProductIdAndIsActiveTrue(Long userId, Long catalogProductId);
}
