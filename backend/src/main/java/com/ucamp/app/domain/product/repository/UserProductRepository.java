package com.ucamp.app.domain.product.repository;

import com.ucamp.app.domain.product.entity.UserProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserProductRepository extends JpaRepository<UserProduct, Long> {

    // 유저가 지금 먹고 있는(is_active = true) 제품 목록
    List<UserProduct> findByUserIdAndIsActiveTrue(Long userId);
}
