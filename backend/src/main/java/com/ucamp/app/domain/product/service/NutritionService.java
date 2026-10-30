package com.ucamp.app.domain.product.service;

import com.ucamp.app.domain.health.entity.HealthProfile;
import com.ucamp.app.domain.health.repository.HealthProfileRepository;
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

    // 예시 제품의 하루 섭취 기준 성분 함량 (예시 데이터)
    // 키는 catalog_products.mfds_item_no, 식약처 제품은 성분 요약에서 함량을 읽음
    private static final Map<String, Map<Nutrient, Double>> SAMPLE_AMOUNTS = Map.of(
            "SAMPLE-0001", Map.of(Nutrient.VITAMIN_C, 100.0, Nutrient.VITAMIN_D, 10.0,
                    Nutrient.MAGNESIUM, 80.0, Nutrient.CALCIUM, 200.0),
            "SAMPLE-0002", Map.of(Nutrient.OMEGA3, 600.0),
            "SAMPLE-0003", Map.of(Nutrient.MAGNESIUM, 200.0),
            // 비타민D 2000IU = 50㎍
            "SAMPLE-0007", Map.of(Nutrient.VITAMIN_D, 50.0)
    );

    private final UserProductRepository userProductRepository;
    private final UserRepository userRepository;
    private final HealthProfileRepository healthProfileRepository;

    public NutritionResponse getNutrition(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        //성별에 맞는 권장량을 쓰려고 건강 정보의 성별을 읽음(없으면 null → 남성 값)
        String gender = null;
        HealthProfile profile = healthProfileRepository.findById(userId).orElse(null);
        if(profile != null) {
            gender = profile.getGender();
        }

        // 1. 먹고 있는 영양제의 성분 함량을 성분별로 더한다
        Map<Nutrient, Double> totals = new EnumMap<>(Nutrient.class);
        boolean sample = false;
        int unsupportedCount = 0; // 함량 정보가 없어 계산에서 빠진 영양제 수
        for (UserProduct userProduct : userProductRepository.findActiveWithCatalogByUserId(userId)) {
            // 처방약 등 영양제가 아닌 것은 영양 계산 대상이 아님
            if (!"SUPPLEMENT".equals(userProduct.getProductKind())) {
                continue;
            }
            CatalogProduct product = userProduct.getCatalogProduct();
            Map<Nutrient, Double> amounts = null;
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
                double before = totals.getOrDefault(nutrient, 0.0);  // 지금까지 더한 양 (없으면 0)
                totals.put(nutrient, before + amounts.get(nutrient));
            }
        }


        // 2. 먹고 있는 성분마다 권장량 대비 섭취율을 계산한다
        List<NutrientIntakeResponse> nutrients = new ArrayList<>();
        for (Nutrient nutrient : Nutrient.values()) {
            //등록한 영양제에 없는 성분은 게이지에 포함하지 않음
            if(!totals.containsKey(nutrient)){
                continue;
            }
            double total = totals.get(nutrient);
            double recommended = nutrient.getRecommendationAmount(gender);
            // 섭취율은 반올림 전 합계로 계산
            int percent = (int) Math.round(total * 100.0 / recommended);
            // 화면 표시용 섭취량: 소수 둘째 자리까지, 0.004처럼 아주 작은 양이 0으로 보이지 않게 그때는 원래 값
            double intake = round2(total);
            if (intake == 0) {
                intake = total;
            }
            nutrients.add(new NutrientIntakeResponse(
                    nutrient.getDisplayName(),
                    nutrient.getUnit(),
                    intake,
                    recommended,
                    percent
            ));
        }
        return new NutritionResponse(userId, nutrients, sample, unsupportedCount);

    }

    // 식약처 성분 요약 글을 성분별 함량으로 바꿈
    // 예: "EPA+DHA 530mg, 루테인 20mg" → 오메가3 530 (게이지에 없는 루테인은 건너뜀)
    private Map<Nutrient, Double> parseSummary(String summary) {
        Map<Nutrient, Double> amounts = new EnumMap<>(Nutrient.class);
        if(summary == null) {
            return amounts;
        }
        for (String part : summary.split(", ")) {
            // 천 단위 쉼표 없애고 단위 표기 통일 (㎎ → mg, μg·ug → ㎍), 공백은 숫자 구분용으로 남김
            String text = part.trim().replace(",", "").replace("㎎", "mg").replace("μg", "㎍").replace("µg", "㎍").replace("ug", "㎍");
            // 성분 이름은 공백을 없앤 글로 찾음 ("비타민 C" → "비타민C")
            Nutrient nutrient = findNutrient(text.replace(" ", ""));
            // 단위 뒤에 붙는 표기 떼기 (예: "11mgα-TE" → "11mg", "700㎍RAE" → "700㎍")
            text = text.replace("α-TE", "").replace("RAE", "").replace("DFE", "").trim();
            // 게이지에 없는 성분이면 건너뜀
            if (nutrient == null) {
                continue;
            }
            // 게이지 단위와 같으면 그대로, 게이지가 ㎍인데 mg로 적혀 있으면 1000배 (예: 비타민 A "0.7mgRAE" → 700㎍)
            String unit = nutrient.getUnit();
            double multiplier = 1;
            if (!text.endsWith(unit)) {
                if ("㎍".equals(unit) && text.endsWith("mg")) {
                    unit = "mg";
                    multiplier = 1000;
                } else {
                    continue;
                }
            }

            // 단위 바로 앞의 숫자 부분 찾기 (뒤에서부터 숫자와 점이 이어지는 곳까지)
            int end = text.length() - unit.length();
            int start = end;
            while (start > 0 && (Character.isDigit(text.charAt(start - 1)) || text.charAt(start - 1) == '.')) {
                start--;
            }
            if (start == end) {
                continue;
            }
            double amount = Double.parseDouble(text.substring(start, end)) * multiplier;

            // EPA, DHA처럼 같은 성분이 두 번 나오면 더함
            double before = amounts.getOrDefault(nutrient, 0.0);
            amounts.put(nutrient, before + amount);
        }
        return amounts;
    }

    // 성분 이름으로 게이지 성분 찾기 (공백을 없앤 글 기준), 게이지에 없는 성분이면 null
    private Nutrient findNutrient(String text) {
        if (text.contains("비타민A")) {
            return Nutrient.VITAMIN_A;
        }
        if (text.contains("비타민B12")) {
            return Nutrient.VITAMIN_B12;
        }
        if (text.contains("비타민B1")) {
            return Nutrient.VITAMIN_B1;
        }
        if (text.contains("비타민B2")) {
            return Nutrient.VITAMIN_B2;
        }
        if (text.contains("비타민B6")) {
            return Nutrient.VITAMIN_B6;
        }
        if (text.contains("비타민C")) {
            return Nutrient.VITAMIN_C;
        }
        if (text.contains("비타민D")) {
            return Nutrient.VITAMIN_D;
        }
        if (text.contains("비타민E")) {
            return Nutrient.VITAMIN_E;
        }
        if (text.contains("엽산")) {
            return Nutrient.FOLATE;
        }
        if (text.contains("칼슘")) {
            return Nutrient.CALCIUM;
        }
        if (text.contains("마그네슘")) {
            return Nutrient.MAGNESIUM;
        }
        if (text.contains("아연")) {
            return Nutrient.ZINC;
        }
        if (text.startsWith("철")) {
            return Nutrient.IRON;
        }
        if (text.contains("EPA") || text.contains("DHA") || text.contains("오메가3")) {
            return Nutrient.OMEGA3;
        }
        return null;
    }

    // 소수 둘째 자리까지 (더하다 생기는 0.30000000000000004 같은 값 정리)
    private double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
