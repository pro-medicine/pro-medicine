package com.ucamp.app.domain.product.service;

import com.ucamp.app.domain.health.entity.HealthProfile;
import com.ucamp.app.domain.health.repository.HealthProfileRepository;
import com.ucamp.app.domain.intake.entity.IntakeSchedule;
import com.ucamp.app.domain.intake.repository.IntakeScheduleRepository;
import com.ucamp.app.domain.product.dto.TodayNutritionResponse;
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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 홈 "오늘 먹은 영양소" (오늘 복용 일정별 성분 함량 + 성분별 권장량).
 * 성분 함량 계산은 마이페이지 영양관리(NutritionService)와 같은 방법을 쓴다.
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
    private final HealthProfileRepository healthProfileRepository;
    private final IntakeScheduleRepository intakeScheduleRepository;
    private final NutritionService nutritionService;

    public TodayNutritionResponse getTodayNutrition(Long userId, LocalDate date) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // 성별에 맞는 권장량 (건강 정보가 없으면 null → 남성 값, 마이페이지 영양관리와 같음)
        String gender = healthProfileRepository.findById(userId).map(HealthProfile::getGender).orElse(null);

        // 홈 타임라인과 같은 "오늘 일정" 목록 (id = scheduleId)
        List<Long> scheduleIds = intakeScheduleRepository.findTodaySupplements(userId, date).stream()
                .map(IntakeScheduleRepository.SupplementRow::getId)
                .toList();
        Map<Long, IntakeSchedule> byId = intakeScheduleRepository.findAllById(scheduleIds).stream()
                .collect(Collectors.toMap(IntakeSchedule::getScheduleId, Function.identity()));
        // 같은 영양제를 오늘 몇 번 먹는지 → 한 번에 하루 함량을 나눠서 먹는다고 본다
        Map<Long, Long> timesPerProduct = byId.values().stream()
                .collect(Collectors.groupingBy(s -> s.getUserProduct().getUserProductId(), Collectors.counting()));

        Set<Nutrient> shown = EnumSet.copyOf(BASE_NUTRIENTS);
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
            Map<Nutrient, Double> daily = nutritionService.dailyAmounts(product.getCatalogProduct());
            if (daily.isEmpty()) {
                unsupported++;
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
                }
            }
            schedules.add(new ScheduleNutrients(id, amounts));
        }

        List<NutrientInfo> nutrients = shown.stream() // EnumSet → Nutrient 선언 순서
                .map(n -> new NutrientInfo(n.name(), n.getDisplayName(), n.getUnit(), n.getRecommendationAmount(gender)))
                .toList();
        return new TodayNutritionResponse(date, nutrients, schedules, unsupported, sample);
    }

    // 소수 셋째 자리까지 (3번에 나눠 먹는 1mg → 0.333)
    private static double round3(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
