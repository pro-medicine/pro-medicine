package com.ucamp.app.support;

import com.ucamp.app.domain.health.entity.HealthProfile;
import com.ucamp.app.domain.health.entity.UserCondition;
import com.ucamp.app.domain.health.entity.UserMedication;
import com.ucamp.app.domain.health.repository.DrugRepository;
import com.ucamp.app.domain.health.repository.HealthProfileRepository;
import com.ucamp.app.domain.health.repository.UserConditionRepository;
import com.ucamp.app.domain.health.repository.UserMedicationRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * V2 테스트·평가용 회원 만들기 (예시 데이터). 설문 없이 프로필·질환·복용 약을 바로 저장한다.
 * medications 항목은 약 마스터 ID(D-001 등)면 drug로, 아니면 이름만 있는 약(unmatched_name)으로 저장한다.
 */
@TestComponent
@RequiredArgsConstructor
public class TestMembers {
    private final UserRepository userRepository;
    private final HealthProfileRepository healthProfileRepository;
    private final UserConditionRepository userConditionRepository;
    private final UserMedicationRepository userMedicationRepository;
    private final DrugRepository drugRepository;

    /** profile 키는 health_profiles 컬럼명 (gender, birth_year, smoking …). 값이 없으면 넣지 않는다 */
    public User create(boolean consent, Map<String, Object> profile, List<String> conditions, List<String> medications) {
        User user = TestUsers.create(userRepository, consent);
        if (!profile.isEmpty()) {
            HealthProfile hp = HealthProfile.create(user);
            profile.forEach((key, value) -> {
                if (value instanceof Number n) {
                    hp.applySurveyValue(key, null, new BigDecimal(n.toString()));
                } else if (value != null) {
                    hp.applySurveyValue(key, value.toString(), null);
                }
            });
            healthProfileRepository.saveAndFlush(hp);
        }
        userConditionRepository.saveAllAndFlush(conditions.stream()
                .map(c -> UserCondition.builder().user(user).conditionName(c).build()).toList());
        userMedicationRepository.saveAllAndFlush(medications.stream()
                .map(m -> drugRepository.findById(m)
                        .map(d -> UserMedication.builder().user(user).drug(d).build())
                        .orElseGet(() -> UserMedication.builder().user(user).unmatchedName(m).build()))
                .toList());
        return user;
    }

    /** V2 조건을 모두 갖춘 기본 회원 (남 46세, 고혈압, 메트포르민) */
    public User fullMale(int birthYear) {
        return create(true, Map.of("gender", "M", "birth_year", birthYear, "smoking", "HALF_PACK"),
                List.of("HYPERTENSION"), List.of("D-001"));
    }
}
