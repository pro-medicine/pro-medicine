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

        // 1. 먹고 있는 영양제의 성분 함량을 성분별로 더한다
        Map<Nutrient, Integer> totals = new EnumMap<>(Nutrient.class);
        boolean sample = false;
        int unsupportedCount = 0; // 함량 정보가 없어 계산에서 빠진 영양제 수
        for (UserProduct userProduct : userProductRepository.findActiveWithCatalogByUserId(userId)) {
            // 처방약 등 영양제가 아닌 것은 영양 계산 대상이 아님
            if (!"SUPPLEMENT".equals(userProduct.getProductKind())) {
                continue;
            }
            CatalogProduct product = userProduct.getCatalogProduct();
            Map<Nutrient, Integer> amounts = null;
            if (product != null) {
                // 예시 제품은 예시 함량, 식약처 제품은 성분 요약 글에서 함량을 읽음
                amounts = SAMPLE_AMOUNTS.get(product.getMfdsItemNo());
                if(amounts != null) {
                    sample = true;
                } else {
                    amounts = parseSummary(product.getIngredientSummary());
                }
            }
            if(amounts == null || amounts.isEmpty()) {
                unsupportedCount++;
                continue;
            }
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
        return new NutritionResponse(userId, nutrients, sample, unsupportedCount);
    }

    // 식약처 성분 요약 글을 성분별 함량으로 바꿈
    // 예: "EPA+DHA 530mg, 루테인 20mg" → 오메가3 530 (게이지에 없는 루테인은 건너뜀)
    private Map<Nutrient, Integer> parseSummary(String summary) {
        Map<Nutrient, Integer> amounts = new EnumMap<>(Nutrient.class);
        if(summary == null) {
            return amounts;
        }
        for (String part : summary.split(", ")) {
            // 천 단위 쉼표 없애고 단위 표기 통일 (㎎ → mg, μg·ug → ㎍), 공백은 숫자 구분용으로 남김
            String text = part.trim().replace(",", "").replace("㎎", "mg").replace("μg", "㎍").replace("µg", "㎍").replace("ug", "㎍");
            // 성분 이름은 공백을 없앤 글로 찾음 ("비타민 C" → "비타민C")
            Nutrient nutrient = findNutrient(text.replace(" ", ""));
            // 게이지에 없는 성분이거나, 단위가 게이지 단위와 다르면 건너뜀
            if (nutrient == null || !text.endsWith(nutrient.getUnit())) {
                continue;
            }

            //단위 바로 앞의 숫자 부분 찾기 (뒤에서부터 숫자와 점이 이어지는 곳까지)
            int end = text.length() - nutrient.getUnit().length();
            int start = end;
            while (start > 0 && (Character.isDigit(text.charAt(start-1)) || text.charAt(start - 1) == '.')) {
                start--;
            }
            if (start == end) {
                continue;
            }
            int amount = (int)Math.round(Double.parseDouble(text.substring(start, end)));

            // EPA, DHA처럼 같은 성분이 두 번 나오면 더함
            int before = amounts.getOrDefault(nutrient, 0);
            amounts.put(nutrient, before + amount);
        }
        return amounts;
    }

    // 성분 이름으로 게이지 성분 찾기 (공백을 없앤 글 기준), 게이지에 없는 성분이면 null
    private Nutrient findNutrient(String text) {
        if (text.contains("비타민C")) {
            return Nutrient.VITAMIN_C;
        }
        if (text.contains("비타민D")) {
            return Nutrient.VITAMIN_D;
        }
        if (text.contains("마그네슘")) {
            return Nutrient.MAGNESIUM;
        }
        if (text.contains("칼슘")) {
            return Nutrient.CALCIUM;
        }
        if (text.contains("EPA") || text.contains("DHA") || text.contains("오메가")) {
            return Nutrient.OMEGA3;
        }
        return null;
    }


}