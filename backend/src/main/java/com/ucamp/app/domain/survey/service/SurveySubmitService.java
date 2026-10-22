package com.ucamp.app.domain.survey.service;

import com.ucamp.app.domain.health.dto.MedicationEntry;
import com.ucamp.app.domain.health.dto.ProfileValue;
import com.ucamp.app.domain.health.entity.HealthProfile;
import com.ucamp.app.domain.health.service.DrugService;
import com.ucamp.app.domain.health.service.HealthProfileService;
import com.ucamp.app.domain.survey.dto.SubmitSurveyRequest;
import com.ucamp.app.domain.survey.dto.SubmitSurveyResponse;
import com.ucamp.app.domain.survey.entity.AnswerType;
import com.ucamp.app.domain.survey.entity.QuestionOption;
import com.ucamp.app.domain.survey.entity.RespondentType;
import com.ucamp.app.domain.survey.entity.ResponseStatus;
import com.ucamp.app.domain.survey.entity.Survey;
import com.ucamp.app.domain.survey.entity.SurveyAnswer;
import com.ucamp.app.domain.survey.entity.SurveyQuestion;
import com.ucamp.app.domain.survey.entity.SurveyResponse;
import com.ucamp.app.domain.survey.entity.SurveyStatus;
import com.ucamp.app.domain.survey.repository.SurveyAnswerRepository;
import com.ucamp.app.domain.survey.repository.SurveyRepository;
import com.ucamp.app.domain.survey.repository.SurveyResponseRepository;
import com.ucamp.app.domain.survey.service.SurveyQueryService.QuestionWithOptions;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.service.UserConsentService;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SurveySubmitService {
    private static final int MIN_BIRTH_YEAR = 1900;

    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final SurveyQueryService surveyQueryService;
    private final SurveyAnswerParser surveyAnswerParser;
    private final UserConsentService userConsentService;
    private final HealthProfileService healthProfileService;
    private final DrugService drugService;

    // 검증을 통과한 답 하나. 선택형은 options, 숫자형은 number, TEXT는 text
    private record ValidAnswer(SurveyQuestion question, List<QuestionOption> options, BigDecimal number, String text) {
    }

    /**
     * 응답 1행 + 답 N행 저장, 회원이면 profile_key가 있는 답을 프로필에 반영. 한 트랜잭션.
     */
    @Transactional
    public SubmitSurveyResponse submit(Long surveyId, Long userId, SubmitSurveyRequest request) {
        Survey survey = surveyRepository.findById(surveyId)
                .filter(s -> s.getStatus() == SurveyStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.SURVEY_NOT_FOUND));
        User user = userId == null ? null : userConsentService.getUser(userId);
        if (request == null || request.answers() == null || request.answers().isEmpty()) {
            throw new BusinessException(ErrorCode.SURVEY_INVALID_ANSWER);
        }

        Map<Long, QuestionWithOptions> questions = surveyQueryService.loadQuestions(survey.getSurveyId()).stream()
                .collect(Collectors.toMap(qo -> qo.question().getQuestionId(), Function.identity()));
        List<ValidAnswer> answers = validate(request.answers(), questions, user);

        boolean includesSensitive = answers.stream().anyMatch(a -> a.question().getIsSensitive());
        SurveyResponse response = surveyResponseRepository.save(SurveyResponse.builder()
                .survey(survey)
                .user(user)
                .respondentType(user == null ? RespondentType.GUEST : RespondentType.MEMBER)
                .includesSensitive(includesSensitive)
                .status(ResponseStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now())
                .build());
        surveyAnswerRepository.saveAll(toEntities(response, answers));

        if (user != null) {
            applyToProfile(user, answers);
        }
        return new SubmitSurveyResponse(response.getResponseId());
    }

    private List<ValidAnswer> validate(List<SubmitSurveyRequest.Answer> requested,
                                       Map<Long, QuestionWithOptions> questions, User user) {
        Set<Long> seen = new HashSet<>();
        List<ValidAnswer> result = new ArrayList<>();
        for (SubmitSurveyRequest.Answer a : requested) {
            if (a == null || a.questionId() == null) {
                throw invalid();
            }
            QuestionWithOptions qo = questions.get(a.questionId());
            if (qo == null || !seen.add(a.questionId())) {
                // 이 설문의 문항이 아니거나 같은 문항 답이 중복
                throw invalid();
            }
            SurveyQuestion q = qo.question();
            if (q.getIsSensitive()) {
                if (user == null) {
                    throw new BusinessException(ErrorCode.SURVEY_SENSITIVE_GUEST);
                }
                if (!user.isSensitiveConsent()) {
                    throw new BusinessException(ErrorCode.SURVEY_SENSITIVE_CONSENT_REQUIRED);
                }
            } else if (user == null && !q.getGuestVisible()) {
                throw invalid();
            }
            result.add(switch (q.getAnswerType()) {
                case SINGLE, MULTI -> validateChoice(q, qo.options(), a.optionIds());
                case NUMBER -> validateNumber(q, a.valueNumber());
                case TEXT -> validateText(q, a.valueText());
            });
        }
        return result;
    }

    private ValidAnswer validateChoice(SurveyQuestion q, List<QuestionOption> options, List<Long> optionIds) {
        if (optionIds == null || optionIds.isEmpty() || optionIds.stream().anyMatch(Objects::isNull)) {
            throw invalid();
        }
        if (q.getAnswerType() == AnswerType.SINGLE && optionIds.size() > 1) {
            throw invalid();
        }
        if (new HashSet<>(optionIds).size() != optionIds.size()) {
            throw invalid();
        }
        Map<Long, QuestionOption> byId = options.stream()
                .collect(Collectors.toMap(QuestionOption::getOptionId, Function.identity()));
        List<QuestionOption> chosen = new ArrayList<>();
        for (Long id : optionIds) {
            QuestionOption option = byId.get(id);
            if (option == null) {
                throw invalid();
            }
            chosen.add(option);
        }
        // 질환 "없음"은 다른 질환과 같이 고를 수 없다
        boolean hasNone = chosen.stream().anyMatch(o -> SurveyCodes.NONE.equals(o.getValueCode()));
        if (SurveyCodes.CONDITIONS.equals(q.getCode()) && hasNone && chosen.size() > 1) {
            throw invalid();
        }
        return new ValidAnswer(q, chosen, null, null);
    }

    private ValidAnswer validateNumber(SurveyQuestion q, BigDecimal value) {
        if (value == null) {
            throw invalid();
        }
        if (HealthProfile.KEY_BIRTH_YEAR.equals(q.getProfileKey())
                && (value.intValue() < MIN_BIRTH_YEAR || value.intValue() > Year.now().getValue()
                || value.stripTrailingZeros().scale() > 0)) {
            throw invalid();
        }
        return new ValidAnswer(q, List.of(), value, null);
    }

    private ValidAnswer validateText(SurveyQuestion q, String text) {
        if (text == null || text.isBlank()) {
            throw invalid();
        }
        if (SurveyCodes.MEDICATIONS.equals(q.getCode())) {
            List<MedicationEntry> meds = surveyAnswerParser.parseMedications(text);
            Set<String> drugIds = meds.stream().map(MedicationEntry::drugId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            if (drugService.findByIds(drugIds).size() != drugIds.size()) {
                throw invalid();
            }
        } else if (SurveyCodes.SUPPLEMENTS.equals(q.getCode())) {
            surveyAnswerParser.parseSupplements(text);
        }
        return new ValidAnswer(q, List.of(), null, text);
    }

    // MULTI는 고른 선택지마다 한 행
    private static List<SurveyAnswer> toEntities(SurveyResponse response, List<ValidAnswer> answers) {
        List<SurveyAnswer> rows = new ArrayList<>();
        for (ValidAnswer a : answers) {
            if (a.options().isEmpty()) {
                rows.add(SurveyAnswer.builder().response(response).question(a.question())
                        .valueNumber(a.number()).valueText(a.text()).build());
                continue;
            }
            for (QuestionOption option : a.options()) {
                rows.add(SurveyAnswer.builder().response(response).question(a.question()).option(option).build());
            }
        }
        return rows;
    }

    private void applyToProfile(User user, List<ValidAnswer> answers) {
        Map<String, ProfileValue> values = new LinkedHashMap<>();
        List<String> conditions = null;
        List<MedicationEntry> medications = null;
        for (ValidAnswer a : answers) {
            String key = a.question().getProfileKey();
            if (key == null) {
                continue;
            }
            if (SurveyCodes.PROFILE_KEY_CONDITIONS.equals(key)) {
                conditions = a.options().stream().map(QuestionOption::getValueCode)
                        .filter(code -> !SurveyCodes.NONE.equals(code)).toList();
            } else if (SurveyCodes.PROFILE_KEY_MEDICATIONS.equals(key)) {
                medications = surveyAnswerParser.parseMedications(a.text());
            } else if (HealthProfile.PROFILE_KEYS.contains(key)) {
                values.put(key, a.options().isEmpty()
                        ? new ProfileValue(null, a.number())
                        : new ProfileValue(a.options().get(0).getValueCode(), null));
            }
        }
        healthProfileService.applySurvey(user, new HashMap<>(values), conditions, medications);
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.SURVEY_INVALID_ANSWER);
    }
}
