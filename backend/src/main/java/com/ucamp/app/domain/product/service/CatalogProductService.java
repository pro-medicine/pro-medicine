package com.ucamp.app.domain.product.service;

import com.ucamp.app.domain.product.dto.CatalogProductSearchResponse;
import com.ucamp.app.domain.product.entity.CatalogProduct;
import com.ucamp.app.domain.product.repository.CatalogProductRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor

@Transactional(readOnly = true)
public class CatalogProductService {
    private static final int KEYWORD_MAX_LENGTH = 50;
    private final CatalogProductRepository catalogProductRepository;

    public List<CatalogProductSearchResponse> search(String keyword) {
        //1. 검색어가 없거나 너무 길면 400
        String trimmed = keyword  == null ? "" : keyword.strip();
        if(trimmed.isEmpty() || trimmed.length() > KEYWORD_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        //2. 제품명 또는 제조사에 검색어가 들어간 제품 푀대 20개
        List<CatalogProductSearchResponse> result = new ArrayList<>();
        for (CatalogProduct product : catalogProductRepository.findTop20ByProductNameContainingOrManufacturerContainingOrderByProductNameAsc(trimmed, trimmed)) {
            result.add(CatalogProductSearchResponse.from(product));
        }
        return result;
    }
}
