package com.ucamp.app.domain.product.service;

import com.ucamp.app.domain.intake.entity.IntakeSchedule;
import com.ucamp.app.domain.intake.repository.IntakeScheduleRepository;
import com.ucamp.app.domain.product.dto.TodayNutritionResponse;
import com.ucamp.app.domain.product.dto.TodayNutritionResponse.IntakeWarning;
import com.ucamp.app.domain.product.dto.TodayNutritionResponse.NutrientInfo;
import com.ucamp.app.domain.product.dto.TodayNutritionResponse.ScheduleNutrients;
import com.ucamp.app.domain.product.entity.UserProduct;
import com.ucamp.app.domain.product.enums.Nutrient;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 홈 "오늘 먹은 영양소" (오늘 복용 일정별 성분 함량 + 성분별 권장량).
 * - 엑셀 영양제(supplements)로 등록한 제품: supplement_nutrients의 하루 함량(1회 함량 × 1일 섭취횟수)
 * - 기존 제품(catalog_products): 마이페이지 영양관리(NutritionService)와 같은 방법
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodayNutritionService {

    /** 아무것도 안 먹었을 때도 0%로 보여줄 대표 성분 */
    static final Set<Nutrient> BASE_NUTRIENTS = EnumSet.of(
            Nutrient.VITAMIN_C, Nutrient.VITAMIN_D, Nutrient.VITAMIN_B12, Nutrient.CALCIUM,
            Nutrient.MAGNESIUM, Nutrient.ZINC, Nutrient.IRON, Nutrient.OMEGA3);

    private final UserRepository userRepository;
    private final IntakeScheduleRepository intakeScheduleRepository;
    private final NutritionService nutritionService;
    private final SupplementService supplementService;
    private final PersonalNutrientStandardService personalNutrientStandardService;
    private final TodayIntakeWarningService todayIntakeWarningService;

    public TodayNutritionResponse getTodayNutrition(Long userId, LocalDate date) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // 설문(v1·v2) 결과로 만든 사용자 정보 → 사람별 하루 권장량 (2025 한국인 영양소 섭취기준, 정보가 없으면 19~29세·남성 기준)
        PersonalNutrientStandardService.Profile profile = personalNutrientStandardService.profile(userId, date);
        Map<Nutrient, Double> recommended = personalNutrientStandardService.recommendedAmounts(profile);

        // 홈 타임라인과 같은 "오늘 일정" 목록 (id = scheduleId)
        List<Long> scheduleIds = intakeScheduleRepository.findTodaySupplements(userId, date).stream()
                .map(IntakeScheduleRepository.SupplementRow::getId)
                .toList();
        Map<Long, IntakeSchedule> byId = intakeScheduleRepository.findAllById(scheduleIds).stream()
                .collect(Collectors.toMap(IntakeSchedule::getScheduleId, Function.identity()));
        // 같은 영양제를 오늘 몇 번 먹는지 → 한 번에 하루 함량을 나눠서 먹는다고 본다
        Map<Long, Long> timesPerProduct = byId.values().stream()
                .collect(Collectors.groupingBy(s -> s.getUserProduct().getUserProductId(), Collectors.counting()));

        // 엑셀 영양제 성분 함량은 한 번에 조회 (영양제 id → 성분 → 하루 함량)
        List<Long> supplementIds = byId.values().stream()
                .map(IntakeSchedule::getUserProduct)
                .filter(p -> p.getSupplement() != null)
                .map(p -> p.getSupplement().getSupplementId())
                .distinct()
                .toList();
        Map<Long, Map<Nutrient, Double>> supplementAmounts = supplementService.dailyAmounts(supplementIds);

        Set<Nutrient> shown = EnumSet.copyOf(BASE_NUTRIENTS);
        // 오늘 먹는 성분 → 먹는 끼니 (주의 문구 판단용: 체크 여부와 상관없이 오늘 일정 전체)
        Map<Nutrient, Set<String>> slotsByNutrient = new EnumMap<>(Nutrient.class);
        List<ScheduleNutrients> schedules = new ArrayList<>();
        int unsupported = 0;
        boolean sample = false;
        for (Long id : scheduleIds) {
            IntakeSchedule schedule = byId.get(id);
            if (schedule == null) {
                continue;
            }
            UserProduct product = schedule.getUserProduct();
            // 처방약 등 영양제가 아닌 것은 영양 계산 대상이 아님 (마이페이지 영양관리와 같음)
            if (!"SUPPLEMENT".equals(product.getProductKind())) {
                continue;
            }
            Map<Nutrient, Double> daily = product.getSupplement() != null
                    ? supplementAmounts.getOrDefault(product.getSupplement().getSupplementId(), Map.of())
                    : nutritionService.dailyAmounts(product.getCatalogProduct());
            if (daily.isEmpty()) {
                unsupported++;   // 함량을 모르는 영양제 (직접 입력, 엑셀에 함량이 없는 제품 등)
                continue;
            }
            sample |= nutritionService.isSampleProduct(product.getCatalogProduct());
            long times = timesPerProduct.getOrDefault(product.getUserProductId(), 1L);
            Map<String, Double> amounts = new LinkedHashMap<>();
            for (Nutrient nutrient : Nutrient.values()) {
                Double amount = daily.get(nutrient);
                if (amount != null) {
                    amounts.put(nutrient.name(), round3(amount / times));
                    shown.add(nutrient);
                    slotsByNutrient.computeIfAbsent(nutrient, k -> new LinkedHashSet<>()).add(schedule.getMealSlot());
                }
            }
            schedules.add(new ScheduleNutrients(id, amounts));
        }

        List<NutrientInfo> nutrients = shown.stream() // EnumSet → Nutrient 선언 순서
                .map(n -> new NutrientInfo(n.name(), n.getDisplayName(), n.getUnit(), recommended.get(n)))
                .toList();
        // 함께 먹을 때 주의할 점 (recommendation_rules)
        List<IntakeWarning> warnings = todayIntakeWarningService.warnings(profile, slotsByNutrient);
        return new TodayNutritionResponse(date, nutrients, schedules, unsupported, sample, warnings);
    }

    // 소수 셋째 자리까지 (3번에 나눠 먹는 1mg → 0.333)
    private static double round3(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
