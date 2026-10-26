package com.ucamp.app.domain.product.service;

import com.ucamp.app.domain.product.dto.ProductResponse;
import com.ucamp.app.domain.product.entity.CatalogProduct;
import com.ucamp.app.domain.product.repository.CatalogProductRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {
    private final CatalogProductRepository catalogProductRepository;

    // 영양제 검색: 검색어가 없으면 전체 목록
    public List<ProductResponse> searchProducts(String keyword) {
        List<CatalogProduct> products;
        if (keyword == null || keyword.isBlank()) {
            products = catalogProductRepository.findAllByOrderByProductNameAsc();
        } else {
            String trimmed = keyword.strip();
            products = catalogProductRepository
                    .findByProductNameContainingOrManufacturerContainingOrderByProductNameAsc(trimmed, trimmed);
        }

        List<ProductResponse> result = new ArrayList<>();
        for (CatalogProduct product : products) {
            result.add(ProductResponse.from(product));
        }
        return result;
    }

    // 영양제 상세(영양정보)
    public ProductResponse getProduct(Long productId) {
        CatalogProduct product = catalogProductRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return ProductResponse.from(product);
    }
}
