package com.ucamp.app.domain.recommendation.service;

import com.ucamp.app.domain.health.dto.HealthProfileSnapshot;
import com.ucamp.app.domain.health.dto.MedicationEntry;
import com.ucamp.app.domain.health.entity.Drug;
import com.ucamp.app.domain.health.service.DrugService;
import com.ucamp.app.domain.health.service.HealthProfileService;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.survey.dto.SurveyResponseDetail;
import com.ucamp.app.domain.survey.dto.SurveyResponseDetail.AnswerValue;
import com.ucamp.app.domain.survey.entity.RespondentType;
import com.ucamp.app.domain.survey.service.SurveyAnswerParser;
import com.ucamp.app.domain.survey.service.SurveyCodes;
import com.ucamp.app.domain.survey.service.SurveyQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * survey_responses 한 건 → 정규화 입력. 회원이면 설문에 없는 값을 프로필로 채운다.
 * 같은 값이 둘 다 있으면 설문 답이 우선 (제출 시 프로필도 같은 값으로 갱신되므로 보통 같다).
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationInputAssembler {
    private final SurveyQueryService surveyQueryService;
    private final SurveyAnswerParser surveyAnswerParser;
    private final HealthProfileService healthProfileService;
    private final DrugService drugService;

    public RecommendationInput assemble(Long responseId) {
        SurveyResponseDetail detail = surveyQueryService.getResponseDetail(responseId);
        boolean member = detail.respondentType() == RespondentType.MEMBER && detail.userId() != null;
        HealthProfileSnapshot profile = member ? healthProfileService.getSnapshot(detail.userId())
                : HealthProfileSnapshot.empty();

        RecommendationInput.RecommendationInputBuilder b = RecommendationInput.builder()
                .respondentType(member ? RecommendationInput.MEMBER : RecommendationInput.GUEST)
                .gender(code(detail, SurveyCodes.GENDER, profile, "gender"))
                .birthYear(birthYear(detail, profile))
                .smoking(code(detail, SurveyCodes.SMOKING, profile, "smoking"))
                .drinking(code(detail, SurveyCodes.DRINKING, profile, "drinking"))
                .caffeine(code(detail, SurveyCodes.CAFFEINE, profile, "caffeine"))
                .dietType(code(detail, SurveyCodes.DIET, profile, "diet_type"))
                .sunExposure(code(detail, SurveyCodes.SUN, profile, "sun_exposure"))
                .height((BigDecimal) profile.value("height"))
                .weight((BigDecimal) profile.value("weight"))
                .sleep(code(detail, SurveyCodes.SLEEP, null, null))
                .fatigue(code(detail, SurveyCodes.FATIGUE, null, null))
                .goals(codes(detail, SurveyCodes.GOAL))
                .currentIntake(intake(detail))
                .responseId(responseId);

        if (member) {
            b.pregnancy(code(detail, SurveyCodes.PREGNANCY, null, null))
                    .conditions(conditions(detail, profile))
                    .medications(medications(detail, profile));
        } else {
            b.conditions(List.of()).medications(List.of());
        }
        return b.build();
    }

    private static String code(SurveyResponseDetail detail, String questionCode, HealthProfileSnapshot profile,
                               String profileKey) {
        AnswerValue a = detail.answer(questionCode);
        if (a != null && a.firstCode() != null) {
            return a.firstCode();
        }
        if (profile == null || profileKey == null) {
            return null;
        }
        Object v = profile.value(profileKey);
        return v == null ? null : v.toString();
    }

    private static Integer birthYear(SurveyResponseDetail detail, HealthProfileSnapshot profile) {
        AnswerValue a = detail.answer(SurveyCodes.BIRTH);
        if (a != null && a.valueNumber() != null) {
            return a.valueNumber().intValue();
        }
        Object v = profile.value("birth_year");
        return v == null ? null : ((BigDecimal) v).intValue();
    }

    private static List<String> codes(SurveyResponseDetail detail, String questionCode) {
        AnswerValue a = detail.answer(questionCode);
        return a == null ? List.of() : List.copyOf(a.valueCodes());
    }

    private static List<String> conditions(SurveyResponseDetail detail, HealthProfileSnapshot profile) {
        AnswerValue a = detail.answer(SurveyCodes.CONDITIONS);
        if (a != null) {
            return a.valueCodes().stream().filter(c -> !SurveyCodes.NONE.equals(c)).toList();
        }
        return profile.conditions();
    }

    private List<RecommendationInput.Medication> medications(SurveyResponseDetail detail, HealthProfileSnapshot profile) {
        AnswerValue a = detail.answer(SurveyCodes.MEDICATIONS);
        if (a == null || a.valueText() == null) {
            return profile.medications().stream()
                    .map(m -> new RecommendationInput.Medication(m.drugId(), m.drugClass(),
                            m.drugId() != null ? m.drugName() : m.unmatchedName()))
                    .toList();
        }
        List<MedicationEntry> entries = surveyAnswerParser.parseMedications(a.valueText());
        Map<String, Drug> drugs = drugService.findByIds(entries.stream()
                .map(MedicationEntry::drugId).filter(Objects::nonNull).toList());
        return entries.stream()
                .map(e -> {
                    Drug d = e.drugId() == null ? null : drugs.get(e.drugId());
                    return d == null
                            ? new RecommendationInput.Medication(null, null, e.name())
                            : new RecommendationInput.Medication(d.getDrugId(), d.getDrugClass(), d.getName());
                })
                .toList();
    }

    private List<RecommendationInput.Intake> intake(SurveyResponseDetail detail) {
        AnswerValue a = detail.answer(SurveyCodes.SUPPLEMENTS);
        if (a == null || a.valueText() == null) {
            return List.of();
        }
        return surveyAnswerParser.parseSupplements(a.valueText()).stream()
                .map(s -> new RecommendationInput.Intake(s.ingredient(), s.amount(), s.unit()))
                .toList();
    }
}
