package com.ucamp.app.domain.product.service;

import com.ucamp.app.domain.product.dto.NutrientIntakeResponse;
import com.ucamp.app.domain.product.dto.NutritionResponse;
import com.ucamp.app.domain.product.entity.CatalogProduct;
import com.ucamp.app.domain.product.entity.UserProduct;
import com.ucamp.app.domain.product.enums.Nutrient;
import com.ucamp.app.domain.product.repository.UserProductRepository;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NutritionService {

    // 예시 제품의 하루 섭취 기준 성분 함량 (예시 데이터, 식약처 연동 시 교체)
    // 키는 catalog_products.mfds_item_no, 목록에 없는 제품은 계산에서 빠짐
    private static final Map<String, Map<Nutrient, Integer>> SAMPLE_AMOUNTS = Map.of(
            "SAMPLE-0001", Map.of(Nutrient.VITAMIN_C, 100, Nutrient.VITAMIN_D, 10,
                    Nutrient.MAGNESIUM, 80, Nutrient.CALCIUM, 200),
            "SAMPLE-0002", Map.of(Nutrient.OMEGA3, 600),
            "SAMPLE-0003", Map.of(Nutrient.MAGNESIUM, 200),
            // 비타민D 2000IU = 50㎍
            "SAMPLE-0007", Map.of(Nutrient.VITAMIN_D, 50)
    );

    private final UserProductRepository userProductRepository;
    private final UserRepository userRepository;

    public NutritionResponse getNutrition(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 1. 먹고 있는 제품의 성분 함량을 성분별로 더한다
        Map<Nutrient, Integer> totals = new EnumMap<>(Nutrient.class);
        boolean sample = false;
        for (UserProduct userProduct : userProductRepository.findActiveWithCatalogByUserId(userId)) {
            CatalogProduct product = userProduct.getCatalogProduct();
            if (product == null) {
                continue; // 직접 입력한 처방약 등은 성분 정보가 없음
            }
            Map<Nutrient, Integer> amounts = SAMPLE_AMOUNTS.get(product.getMfdsItemNo());
            if (amounts == null) {
                continue;
            }
            sample = true;
            for (Nutrient nutrient : amounts.keySet()) {
                int before = totals.getOrDefault(nutrient, 0);  // 지금까지 더한 양 (없으면 0)
                totals.put(nutrient, before + amounts.get(nutrient));
            }
        }

        // 2. 성분마다 권장량 대비 섭취율을 계산한다 (먹는 게 없으면 0)
        List<NutrientIntakeResponse> nutrients = new ArrayList<>();
        for (Nutrient nutrient : Nutrient.values()) {
            int intake = totals.getOrDefault(nutrient, 0);
            int percent = (int) Math.round(intake * 100.0 / nutrient.getRecommendedAmount());
            nutrients.add(new NutrientIntakeResponse(
                    nutrient.getDisplayName(),
                    nutrient.getUnit(),
                    intake,
                    nutrient.getRecommendedAmount(),
                    percent
            ));
        }
        return new NutritionResponse(userId, nutrients, sample);
    }
}
