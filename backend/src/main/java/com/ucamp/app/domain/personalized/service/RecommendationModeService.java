package com.ucamp.app.domain.personalized.service;

import com.ucamp.app.domain.health.dto.HealthProfileSnapshot;
import com.ucamp.app.domain.health.entity.HealthProfile;
import com.ucamp.app.domain.health.service.HealthProfileService;
import com.ucamp.app.domain.personalized.dto.RecommendationModeResponse;
import com.ucamp.app.domain.survey.repository.SurveyAnswerRepository;
import com.ucamp.app.domain.survey.service.SurveyCodes;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.service.UserConsentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 회원 V1/V2 분기 (설계 D2). 건강정보 있음 = ① 성별·출생 연도 ② 민감정보 동의 ③ 질환·복용 약을 한 번이라도 답함.
 * "답함"은 user_conditions / user_medications 행이 있거나, 회원 설문 응답에 Q-CONDITIONS / Q-MEDICATIONS 답이 있는 것 ("없음" 포함).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationModeService {
    public static final String GENDER = "GENDER";
    public static final String BIRTH_YEAR = "BIRTH_YEAR";
    public static final String SENSITIVE_CONSENT = "SENSITIVE_CONSENT";
    public static final String CONDITIONS_ANSWERED = "CONDITIONS_ANSWERED";
    public static final String MEDICATIONS_ANSWERED = "MEDICATIONS_ANSWERED";

    private final UserConsentService userConsentService;
    private final HealthProfileService healthProfileService;
    private final SurveyAnswerRepository surveyAnswerRepository;

    public RecommendationModeResponse mode(Long userId) {
        User user = userConsentService.getUser(userId);
        HealthProfileSnapshot profile = healthProfileService.getSnapshot(userId);
        List<String> missing = new ArrayList<>();
        if (profile.value(HealthProfile.KEY_GENDER) == null) {
            missing.add(GENDER);
        }
        if (profile.value(HealthProfile.KEY_BIRTH_YEAR) == null) {
            missing.add(BIRTH_YEAR);
        }
        if (!user.isSensitiveConsent()) {
            missing.add(SENSITIVE_CONSENT);
        }
        if (profile.conditions().isEmpty() && !answered(userId, SurveyCodes.CONDITIONS)) {
            missing.add(CONDITIONS_ANSWERED);
        }
        if (profile.medications().isEmpty() && !answered(userId, SurveyCodes.MEDICATIONS)) {
            missing.add(MEDICATIONS_ANSWERED);
        }
        return new RecommendationModeResponse(missing.isEmpty() ? RecommendationModeResponse.V2
                : RecommendationModeResponse.V1, List.copyOf(missing));
    }

    private boolean answered(Long userId, String questionCode) {
        return surveyAnswerRepository.existsByResponse_User_IdAndQuestion_Code(userId, questionCode);
    }
}
