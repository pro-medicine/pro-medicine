package com.ucamp.app.domain.health.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthProfileService {
    private final HealthProfileRepository healthProfileRepository;
    private final UserConditionRepository userConditionRepository;
    private final UserMedicationRepository userMedicationRepository;
    private final DrugService drugService;

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
}
