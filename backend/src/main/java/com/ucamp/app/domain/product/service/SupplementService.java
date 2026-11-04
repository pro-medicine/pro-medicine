package com.ucamp.app.domain.product.service;

import com.ucamp.app.domain.product.dto.SupplementSearchResponse;
import com.ucamp.app.domain.product.entity.Supplement;
import com.ucamp.app.domain.product.entity.SupplementNutrient;
import com.ucamp.app.domain.product.enums.Nutrient;
import com.ucamp.app.domain.product.repository.SupplementNutrientRepository;
import com.ucamp.app.domain.product.repository.SupplementRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 엑셀 영양제(supplements) 검색과 성분 함량 조회.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplementService {

    private static final int KEYWORD_MAX_LENGTH = 50;

    private final SupplementRepository supplementRepository;
    private final SupplementNutrientRepository supplementNutrientRepository;

    /** 제품명 또는 제조사에 검색어가 들어간 영양제 최대 20개. 검색어가 없거나 너무 길면 400 */
    public List<SupplementSearchResponse> search(String keyword) {
        String trimmed = keyword == null ? "" : keyword.strip();
        if (trimmed.isEmpty() || trimmed.length() > KEYWORD_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return supplementRepository
                .findTop20ByProductNameContainingOrManufacturerContainingOrderByProductNameAsc(trimmed, trimmed)
                .stream()
                .map(SupplementSearchResponse::from)
                .toList();
    }

    /**
     * 영양제별 하루 성분 함량 (영양제 id → 성분 → 하루 함량).
     * 함량 정보가 없는 영양제는 결과에 없다. 모르는 성분 코드는 건너뛴다.
     */
    public Map<Long, Map<Nutrient, Double>> dailyAmounts(Collection<Long> supplementIds) {
        Map<Long, Map<Nutrient, Double>> result = new HashMap<>();
        if (supplementIds == null || supplementIds.isEmpty()) {
            return result;
        }
        for (SupplementNutrient row : supplementNutrientRepository.findBySupplementSupplementIdIn(supplementIds)) {
            Nutrient nutrient = toNutrient(row.getNutrientCode());
            if (nutrient == null) {
                continue;
            }
            Long id = row.getSupplement().getSupplementId();
            result.computeIfAbsent(id, k -> new EnumMap<>(Nutrient.class))
                    .put(nutrient, row.getAmountPerDay().doubleValue());
        }
        return result;
    }

    public Supplement get(Long supplementId) {
        return supplementRepository.findById(supplementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private static Nutrient toNutrient(String code) {
        try {
            return Nutrient.valueOf(code);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
