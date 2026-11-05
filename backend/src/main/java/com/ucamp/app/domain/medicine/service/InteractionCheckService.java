package com.ucamp.app.domain.medicine.service;

import com.ucamp.app.domain.health.entity.HealthProfile;
import com.ucamp.app.domain.health.repository.HealthProfileRepository;
import com.ucamp.app.domain.medicine.dto.*;
import com.ucamp.app.domain.medicine.entity.MedicineIngredient;
import com.ucamp.app.domain.medicine.entity.MedicineProduct;
import com.ucamp.app.domain.medicine.repository.MedicineIngredientRepository;
import com.ucamp.app.domain.medicine.repository.MedicineInteractionRepository;
import com.ucamp.app.domain.medicine.repository.MedicineProductRepository;
import com.ucamp.app.domain.prescription.repository.PrescriptionRepository;
import com.ucamp.app.domain.product.repository.UserProductRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InteractionCheckService {

    private static final List<String> SEVERITY_ORDER = List.of("AVOID", "CAUTION", "TIMING", "INFO");
    private static final String SRC_REVIEW = "REVIEW-NEEDED";

    private final MedicineProductRepository medicineProductRepository;
    private final MedicineIngredientRepository medicineIngredientRepository;
    private final MedicineInteractionRepository medicineInteractionRepository;
    private final UserProductRepository userProductRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final HealthProfileRepository healthProfileRepository;

    public List<MedicineSummary> list() {
        return medicineProductRepository.findByActiveTrueOrderByMedicineIdAsc()
                .stream()
                .map(m -> new MedicineSummary(m.getMedicineId(), m.shownName(), m.getKind())).toList();
    }

    public List<MedicineSummary> search(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }

        return medicineProductRepository
                .searchActive(q.trim(), org.springframework.data.domain.PageRequest.of(0, 20))
                .stream()
                .map(m -> new MedicineSummary(
                        m.getMedicineId(),
                        m.shownName(),
                        m.getKind()
                ))
                .toList();
    }

    public InteractionCheckResponse check(Long userId, Long medicineId, LocalDate startAt, LocalDate endAt, Long excludePrescriptionId) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        MedicineProduct medicine = medicineProductRepository.findById(medicineId)
                .filter(MedicineProduct::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDICINE_NOT_FOUND));
        List<MedicineIngredient> actives = medicineIngredientRepository.findByMedicineId(medicineId);
        Set<String> activeCodes = actives.stream()
                .map(MedicineIngredient::getActiveCode)
                .collect(Collectors.toSet());
        Map<String, List<String>> productsByCode = new LinkedHashMap<>();
        userProductRepository.findActiveSupplementNutrientIngredients(userId)
                .forEach(r -> productsByCode
                        .computeIfAbsent(r.getIngredientCode(), k -> new ArrayList<>())
                        .add(r.getProductName()));

        Set<String> supplementCodes = productsByCode.keySet();

        Map<String, InteractionWarning> merged = new LinkedHashMap<>();

        // 1) 약 x 영양제
        if (!activeCodes.isEmpty() && !supplementCodes.isEmpty()) {
            medicineInteractionRepository.findActiveMatches(activeCodes, supplementCodes)
                    .forEach(i -> merged.putIfAbsent(
                            i.getSupplementIngredientCode() + "|" + i.getMessageKo(),
                            new InteractionWarning(
                                    i.getSeverity(),
                                    i.getSupplementIngredientCode(),
                                    productsByCode.get(i.getSupplementIngredientCode()),
                                    i.getMessageKo(),
                                    i.getSourceId()
                            )
                    ));
        }

        // 2) 기간이 겹치는 다른 약과 같은 주성분 (supplementProducts 자리에 '겹치는 약 이름'을 담는다)
        // 2) 복용 날짜가 실제로 겹치는 다른 약과 같은 주성분
        LocalDate s = startAt != null ? startAt : LocalDate.now();
        LocalDate e = endAt != null ? endAt : s.plusDays(2);

        Map<LocalDate, Map<String, Set<String>>> dupByDate = new TreeMap<>();

        if (!activeCodes.isEmpty()) {
            prescriptionRepository.findOverlapCandidates(userId, activeCodes, s.atStartOfDay(), e.plusDays(1).atStartOfDay(), excludePrescriptionId == null ? -1L : excludePrescriptionId)
                    .forEach(r -> {
                        LocalDate otherEnd = r.getEndAt() != null ? r.getEndAt() : r.getStartAt().plusDays(2);
                        LocalDate overlapStart = r.getStartAt().isAfter(s) ? r.getStartAt() : s;
                        LocalDate overlapEnd = otherEnd.isBefore(e) ? otherEnd : e;
                        for (LocalDate date = overlapStart; !date.isAfter(overlapEnd); date = date.plusDays(1)) {
                            dupByDate
                                    .computeIfAbsent(date, k -> new LinkedHashMap<>())
                                    .computeIfAbsent(r.getName(), k -> new LinkedHashSet<>())
                                    .add(r.getActiveNameKo());
                        }
                    });
        }
        List<DatedInteractionWarning> datedWarnings = new ArrayList<>();
        dupByDate.forEach((date, medicines) ->
                medicines.forEach((name, names) -> {
                    InteractionWarning warning = new InteractionWarning(
                            "CAUTION",
                            "DUPLICATE_INGREDIENT",
                            List.of(name),
                            "같은 성분(" + String.join(", ", names)
                                    + ")이 들어 있어요. 함께 드시면 과량이 될 수 있으니 용량을 확인하세요",
                            SRC_REVIEW
                    );
                    datedWarnings.add(new DatedInteractionWarning(date, warning));
                })
        );
        // 3) 아세트아미노펜 × 잦은 음주
        if (activeCodes.contains("ACETAMINOPHEN") && "HIGH".equals(findDrinking(userId))) {
            merged.put("HABIT|DRINKING_HIGH|ACETAMINOPHEN",
                    new InteractionWarning("CAUTION", "ALCOHOL", List.of("음주"),
                            "술을 자주 드시면 아세트아미노펜은 간에 부담이 될 수 있어요. 드시기 전에 약사와 상담하세요",
                            SRC_REVIEW));
        }

        List<InteractionWarning> warnings = merged.values().stream()
                .sorted(Comparator.comparingInt(w -> SEVERITY_ORDER.indexOf(w.severity())))
                .toList();

        return new InteractionCheckResponse(true, medicine.shownName(),
                (int) userProductRepository.countSupplementsWithoutIngredients(userId), warnings, datedWarnings);
    }

    private String findDrinking(Long userId) {
        // HealthProfile의 @Id가 userId라서 findById로 조회
        return healthProfileRepository.findById(userId).map(HealthProfile::getDrinking).orElse(null);
    }
}
