package com.ucamp.app.domain.product.service;

import com.ucamp.app.domain.product.enums.Nutrient;
import com.ucamp.app.domain.recommendation.dose.Standard;
import com.ucamp.app.domain.recommendation.dose.StandardsCache;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.rule.ConditionCodeResolver;
import com.ucamp.app.domain.recommendation.service.RecommendationInputAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 설문(v1·v2) 결과로 사람마다 다른 14개 성분의 하루 권장량을 정한다. (홈 "오늘 먹은 영양소"에서 사용)
 *
 * 1. 설문 정보 모으기: 성별·출생연도·흡연 등은 health_profiles(v1·v2 설문이 저장),
 *    임신·수유는 가장 최근 설문 답(v1 Q-PREGNANCY 또는 v2 맞춤 질문 회차)
 *    → 추천 기능과 같은 방법(RecommendationInputAssembler, ConditionCodeResolver)으로 조건 코드를 만든다.
 * 2. ingredient_standards(2025 한국인 영양소 섭취기준)에서 성분마다 성별·나이·조건에 맞는 행을 고른다.
 *    (고르는 규칙은 추천 기능의 DoseCalculator.select와 같다)
 * 3. 정보가 없으면 2025 성인 19~29세 기준(나이 25세로 계산), 성별을 모르면 남성 기준.
 *    기준표에 행이 없는 성분은 Nutrient enum의 기본값을 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonalNutrientStandardService {

    static final String DEFAULT_GENDER = "M";
    static final int DEFAULT_AGE = 25;   // 정보가 없으면 19~29세 기준
    static final int MIN_AGE = 19;       // 기준표는 성인(19세 이상)만 있음

    private final RecommendationInputAssembler recommendationInputAssembler;
    private final ConditionCodeResolver conditionCodeResolver;
    private final StandardsCache standardsCache;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 설문 결과로 만든 사용자 정보 (권장량·주의 문구 계산에 같이 쓴다).
     *
     * @param gender          M 또는 F (모르면 M)
     * @param age             나이 (모르면 25, 19세 미만이면 19)
     * @param conditions      조건 코드 (SMOKING_CURRENT, PREGNANT_OR_LACTATING, 약 분류, 질환 코드 등)
     * @param medicationNames 약 분류 코드 → 사용자가 입력한 약 이름 (주의 문구에 "와파린"처럼 보여줄 때)
     */
    public record Profile(String gender, int age, Set<String> conditions, Map<String, String> medicationNames) {
    }

    /** 설문(v1·v2) 결과를 모아 사용자 정보를 만든다 */
    public Profile profile(Long userId, LocalDate date) {
        RecommendationInput input = recommendationInputAssembler.assembleForMember(userId, latestPregnancy(userId));
        String gender = "F".equals(input.gender()) ? "F" : DEFAULT_GENDER;
        int age = input.birthYear() == null ? DEFAULT_AGE : Math.max(MIN_AGE, date.getYear() - input.birthYear());
        Map<String, String> medicationNames = new LinkedHashMap<>();
        for (RecommendationInput.Medication m : input.medicationsOrEmpty()) {
            if (m.drugClass() != null && m.name() != null) {
                medicationNames.putIfAbsent(m.drugClass(), m.name());
            }
        }
        return new Profile(gender, age, conditionCodeResolver.resolve(input), medicationNames);
    }

    /** 사용자에게 맞는 성분별 하루 권장량 */
    public Map<Nutrient, Double> recommendedAmounts(Long userId, LocalDate date) {
        return recommendedAmounts(profile(userId, date));
    }

    public Map<Nutrient, Double> recommendedAmounts(Profile profile) {
        List<Standard> standards = standardsCache.standards();
        Map<Nutrient, Double> result = new EnumMap<>(Nutrient.class);
        for (Nutrient nutrient : Nutrient.values()) {
            double amount = select(nutrient.name(), profile.gender(), profile.age(), profile.conditions(), standards)
                    .map(Standard::recommendedAmount)
                    .filter(Objects::nonNull)
                    .map(v -> v.doubleValue())
                    .orElse(nutrient.getRecommendationAmount(profile.gender()));
            result.put(nutrient, amount);
        }
        return result;
    }

    /**
     * 성분 하나에 맞는 기준 행. 추천 기능의 DoseCalculator.select와 같은 규칙:
     * 성별(ALL 포함)·나이 범위·조건(없거나 내가 가진 조건)이 맞는 행 중 조건 행 > 성별 지정 행 > 좁은 나이 범위 순
     */
    static Optional<Standard> select(String code, String gender, int age, Set<String> conditions,
                                     List<Standard> standards) {
        return standards.stream()
                .filter(s -> s.ingredientCode().equals(code))
                .filter(s -> Standard.ALL.equals(s.gender()) || Objects.equals(s.gender(), gender))
                .filter(s -> age >= s.ageMin() && (s.ageMax() == null || age <= s.ageMax()))
                .filter(s -> s.conditionCode() == null || conditions.contains(s.conditionCode()))
                .max(Comparator
                        .comparingInt((Standard s) -> s.conditionCode() != null ? 1 : 0)
                        .thenComparingInt(s -> Standard.ALL.equals(s.gender()) ? 0 : 1)
                        .thenComparingInt(s -> s.ageMax() == null ? Integer.MIN_VALUE : -(s.ageMax() - s.ageMin())));
    }

    /**
     * 가장 최근에 답한 임신·수유 여부 (PREGNANT, LACTATING, POSSIBLE, NOT_APPLICABLE 등). 답이 없으면 null.
     * - v1: 제출한 설문의 Q-PREGNANCY 답
     * - v2: 맞춤 질문 회차에 저장된 답
     * 둘 다 있으면 더 최근 것을 쓴다.
     */
    String latestPregnancy(Long userId) {
        Answer v1 = first(jdbcTemplate.query("""
                SELECT sr.submitted_at AS answered_at, qo.value_code AS answer
                FROM survey_responses sr
                JOIN survey_answers sa ON sa.response_id = sr.response_id
                JOIN survey_questions q ON q.question_id = sa.question_id
                JOIN question_options qo ON qo.option_id = sa.option_id
                WHERE sr.user_id = ? AND sr.status = 'SUBMITTED' AND q.code = 'Q-PREGNANCY'
                ORDER BY sr.submitted_at DESC
                LIMIT 1
                """, (rs, i) -> new Answer(rs.getTimestamp("answered_at"), rs.getString("answer")), userId));
        Answer v2 = first(jdbcTemplate.query("""
                SELECT started_at AS answered_at, pregnancy AS answer
                FROM ai_question_sessions
                WHERE user_id = ? AND pregnancy IS NOT NULL
                ORDER BY started_at DESC
                LIMIT 1
                """, (rs, i) -> new Answer(rs.getTimestamp("answered_at"), rs.getString("answer")), userId));
        if (v1 == null) {
            return v2 == null ? null : v2.answer();
        }
        if (v2 == null) {
            return v1.answer();
        }
        return v1.isAfter(v2) ? v1.answer() : v2.answer();
    }

    private static Answer first(List<Answer> list) {
        return list.isEmpty() ? null : list.get(0);
    }

    private record Answer(Timestamp answeredAt, String answer) {
        boolean isAfter(Answer other) {
            if (answeredAt == null) return false;
            if (other.answeredAt == null) return true;
            return answeredAt.after(other.answeredAt);
        }
    }
}
