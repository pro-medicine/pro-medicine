package com.ucamp.app.domain.health.service;

import com.ucamp.app.domain.health.dto.HealthProfileRequest;
import com.ucamp.app.domain.health.dto.HealthProfileResponse;
import com.ucamp.app.domain.health.dto.HealthProfileSnapshot;
import com.ucamp.app.domain.health.dto.MedicationEntry;
import com.ucamp.app.domain.health.dto.MedicationInfo;
import com.ucamp.app.domain.health.dto.ProfileValue;
import com.ucamp.app.domain.health.entity.Drug;
import com.ucamp.app.domain.health.entity.HealthProfile;
import com.ucamp.app.domain.health.entity.UserCondition;
import com.ucamp.app.domain.health.entity.UserMedication;
import com.ucamp.app.domain.health.repository.HealthProfileRepository;
import com.ucamp.app.domain.health.repository.UserConditionRepository;
import com.ucamp.app.domain.health.repository.UserMedicationRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthProfileService {
    private static final BigDecimal MAX_MEASURE = new BigDecimal("9999.9");

    private final HealthProfileRepository healthProfileRepository;
    private final UserConditionRepository userConditionRepository;
    private final UserMedicationRepository userMedicationRepository;
    private final DrugService drugService;
    private final UserRepository userRepository;

    // 프로필 · 질환 · 복용 약을 한 번에 조회 (쿼리 3개)
    public HealthProfileSnapshot getSnapshot(Long userId) {
        HealthProfile profile = healthProfileRepository.findById(userId).orElse(null);
        List<String> conditions = userConditionRepository.findByUser_IdOrderByUserConditionIdAsc(userId).stream()
                .map(UserCondition::getConditionName)
                .toList();
        List<MedicationInfo> medications = userMedicationRepository.findWithDrugByUserId(userId).stream()
                .map(HealthProfileService::toInfo)
                .toList();
        if (profile == null) {
            return new HealthProfileSnapshot(false, Map.of(), null, conditions, medications);
        }
        return new HealthProfileSnapshot(true, HealthProfileSnapshot.valuesOf(profile), profile.getUpdatedAt(),
                conditions, medications);
    }

    /**
     * 프로필 · 질환 · 복용 약 중 가장 최근 변경 시각 (없으면 null). 알림 진입 시 추천 재사용 판단용.
     * 행 삭제만 있었던 변경은 남는 시각이 없어 잡지 못한다 (지금은 삭제가 설문 제출 때만 일어나 새 추천이 만들어짐).
     */
    public LocalDateTime lastChangedAt(Long userId) {
        LocalDateTime latest = healthProfileRepository.findById(userId).map(HealthProfile::getUpdatedAt).orElse(null);
        for (LocalDateTime t : new LocalDateTime[]{
                userConditionRepository.findLastCreatedAt(userId), userMedicationRepository.findLastUpdatedAt(userId)}) {
            if (t != null && (latest == null || t.isAfter(latest))) {
                latest = t;
            }
        }
        return latest;
    }

    /**
     * 설문 제출 결과를 프로필에 반영한다. 호출한 쪽(설문 제출) 트랜잭션에 참여한다.
     *
     * @param conditions  null이면 질환 문항에 답하지 않은 것(변경 없음), 빈 목록이면 기존 행만 삭제
     * @param medications null이면 변경 없음, 빈 목록이면 기존 행만 삭제
     */
    @Transactional
    public void applySurvey(User user, Map<String, ProfileValue> values,
                            List<String> conditions, List<MedicationEntry> medications) {
        if (!values.isEmpty()) {
            HealthProfile profile = healthProfileRepository.findById(user.getId())
                    .orElseGet(() -> healthProfileRepository.save(HealthProfile.create(user)));
            values.forEach((key, v) -> profile.applySurveyValue(key, v.valueCode(), v.valueNumber()));
        }
        if (conditions != null) {
            userConditionRepository.deleteByUserId(user.getId());
            userConditionRepository.saveAll(conditions.stream()
                    .map(c -> UserCondition.builder().user(user).conditionName(c).build())
                    .toList());
        }
        if (medications != null) {
            userMedicationRepository.deleteByUserId(user.getId());
            Map<String, Drug> drugs = drugService.findByIds(medications.stream()
                    .map(MedicationEntry::drugId).filter(Objects::nonNull).toList());
            userMedicationRepository.saveAll(medications.stream()
                    .map(m -> UserMedication.builder()
                            .user(user)
                            .drug(m.drugId() == null ? null : drugs.get(m.drugId()))
                            .unmatchedName(m.drugId() == null ? m.name() : null)
                            .build())
                    .toList());
        }
    }

    private static MedicationInfo toInfo(UserMedication m) {
        Drug drug = m.getDrug();
        if (drug == null) {
            return new MedicationInfo(null, null, null, m.getUnmatchedName());
        }
        return new MedicationInfo(drug.getDrugId(), drug.getName(), drug.getDrugClass(), m.getUnmatchedName());
    }

    public HealthProfileResponse getHealthProfile(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return healthProfileRepository.findById(userId)
                .map(HealthProfileResponse::from)
                .orElseGet(() -> HealthProfileResponse.empty(userId));
    }

    // 건강 정보가 없으면 새로 만들고, 있으면 전체 항목을 요청 값으로 바꿈
    @Transactional
    public HealthProfileResponse saveHealthProfile(Long userId, HealthProfileRequest request) {
        validate(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 저장 응답과 이후 조회 값이 같도록 DB와 같은 소수점 1자리로 맞춤 (170.50 → 170.5)
        BigDecimal height = toMeasureScale(request.height());
        BigDecimal weight = toMeasureScale(request.weight());

        HealthProfile profile = healthProfileRepository.findById(userId).orElse(null);
        if (profile == null) {
            profile = healthProfileRepository.save(HealthProfile.builder()
                    .user(user)
                    .birthYear(request.birthYear())
                    .gender(request.gender())
                    .height(height)
                    .weight(weight)
                    .smoking(request.smoking())
                    .drinking(request.drinking())
                    .caffeine(request.caffeine())
                    .dietType(request.dietType())
                    .sunExposure(request.sunExposure())
                    .build());
        } else {
            profile.update(request.birthYear(), request.gender(), height, weight,
                    request.smoking(), request.drinking(), request.caffeine(), request.dietType(), request.sunExposure());
        }
        // updatedAt을 응답에 담기 위해 바로 반영
        healthProfileRepository.flush();
        return HealthProfileResponse.from(profile);
    }

    // health_profiles CHECK 제약과 컬럼 길이에 맞춤
    private void validate(HealthProfileRequest request) {
        if (request == null
                || (request.birthYear() != null && (request.birthYear() < 1900 || request.birthYear() > 2100))
                || isInvalidMeasure(request.height())
                || isInvalidMeasure(request.weight())
                || isTooLong(request.gender(), 10)
                || isTooLong(request.smoking(), 20)
                || isTooLong(request.drinking(), 20)
                || isTooLong(request.caffeine(), 20)
                || isTooLong(request.dietType(), 20)
                || isTooLong(request.sunExposure(), 20)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    // validate()를 통과한 값만 들어오므로 반올림 없이 자릿수만 맞춰짐 (반올림이 필요하면 예외)
    private BigDecimal toMeasureScale(BigDecimal value) {
        return value == null ? null : value.setScale(1, RoundingMode.UNNECESSARY);
    }

    // height, weight는 DECIMAL(5,1): 0 초과, 소수점 아래 1자리 이하, 최대 9999.9
    private boolean isInvalidMeasure(BigDecimal value) {
        if (value == null) {
            return false;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.signum() <= 0
                || normalized.scale() > 1
                || normalized.compareTo(MAX_MEASURE) > 0;
    }

    private boolean isTooLong(String value, int max) {
        return value != null && value.length() > max;
    }
}
