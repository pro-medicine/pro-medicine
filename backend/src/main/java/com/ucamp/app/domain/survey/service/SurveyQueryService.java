package com.ucamp.app.domain.survey.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.ucamp.app.domain.health.dto.HealthProfileSnapshot;
import com.ucamp.app.domain.health.dto.MedicationInfo;
import com.ucamp.app.domain.health.service.HealthProfileService;
import com.ucamp.app.domain.survey.dto.ActiveSurveyResponse;
import com.ucamp.app.domain.survey.dto.SurveyResponseDetail;
import com.ucamp.app.domain.survey.entity.AnswerType;
import com.ucamp.app.domain.survey.entity.QuestionOption;
import com.ucamp.app.domain.survey.entity.RespondentType;
import com.ucamp.app.domain.survey.entity.Survey;
import com.ucamp.app.domain.survey.entity.SurveyAnswer;
import com.ucamp.app.domain.survey.entity.SurveyQuestion;
import com.ucamp.app.domain.survey.entity.SurveyResponse;
import com.ucamp.app.domain.survey.entity.SurveyStatus;
import com.ucamp.app.domain.survey.repository.QuestionOptionRepository;
import com.ucamp.app.domain.survey.repository.SurveyAnswerRepository;
import com.ucamp.app.domain.survey.repository.SurveyQuestionRepository;
import com.ucamp.app.domain.survey.repository.SurveyRepository;
import com.ucamp.app.domain.survey.repository.SurveyResponseRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.service.UserConsentService;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyQueryService {
    // 프로필 전체 갱신일이 이 기간을 넘으면 미리 채운 값을 다시 확인하도록 표시
    private static final int CONFIRM_AFTER_MONTHS = 6;

    private final SurveyRepository surveyRepository;
    private final SurveyQuestionRepository surveyQuestionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final UserConsentService userConsentService;
    private final HealthProfileService healthProfileService;
    private final ObjectMapper objectMapper;

    public record QuestionWithOptions(SurveyQuestion question, List<QuestionOption> options) {
    }

    public ActiveSurveyResponse getActiveSurvey(Long userId) {
        Survey survey = surveyRepository.findFirstByStatusOrderBySurveyIdDesc(SurveyStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.SURVEY_NOT_FOUND));

        User user = userId == null ? null : userConsentService.getUser(userId);
        boolean member = user != null;
        boolean includeSensitive = member && user.isSensitiveConsent();
        HealthProfileSnapshot snapshot = member ? healthProfileService.getSnapshot(userId) : HealthProfileSnapshot.empty();

        List<ActiveSurveyResponse.Question> questions = new ArrayList<>();
        for (QuestionWithOptions qo : loadQuestions(survey.getSurveyId())) {
            SurveyQuestion q = qo.question();
            if (!visible(q, member, includeSensitive)) {
                continue;
            }
            List<ActiveSurveyResponse.Option> options = qo.options().stream()
                    .map(o -> new ActiveSurveyResponse.Option(o.getOptionId(), o.getValueCode(), o.getLabel()))
                    .toList();
            questions.add(new ActiveSurveyResponse.Question(q.getQuestionId(), q.getCode(), q.getQuestionText(),
                    q.getAnswerType().name(), q.getIsSensitive(), options,
                    member ? prefill(q, qo.options(), snapshot) : null));
        }

        boolean needsConfirm = snapshot.exists() && snapshot.updatedAt() != null
                && snapshot.updatedAt().isBefore(LocalDateTime.now().minusMonths(CONFIRM_AFTER_MONTHS));
        return new ActiveSurveyResponse(survey.getSurveyId(), survey.getVersion(),
                member ? RespondentType.MEMBER.name() : RespondentType.GUEST.name(), needsConfirm, questions);
    }

    // 설문의 문항과 선택지를 표시 순서대로 읽는다
    public List<QuestionWithOptions> loadQuestions(Long surveyId) {
        List<SurveyQuestion> questions = surveyQuestionRepository.findBySurvey_SurveyIdOrderByDisplayOrderAsc(surveyId);
        // 선택지는 설문 단위로 한 번에 읽고 문항별로 나눈다 (쿼리 2개)
        Map<Long, List<QuestionOption>> optionsByQuestion = new HashMap<>();
        for (QuestionOption o : questionOptionRepository.findByQuestion_Survey_SurveyIdOrderByDisplayOrderAsc(surveyId)) {
            optionsByQuestion.computeIfAbsent(o.getQuestion().getQuestionId(), k -> new ArrayList<>()).add(o);
        }
        List<QuestionWithOptions> result = new ArrayList<>();
        for (SurveyQuestion q : questions) {
            result.add(new QuestionWithOptions(q, optionsByQuestion.getOrDefault(q.getQuestionId(), List.of())));
        }
        return result;
    }

    // 비회원: guest_visible이고 민감하지 않은 문항만. 회원: 민감 문항은 동의했을 때만
    static boolean visible(SurveyQuestion q, boolean member, boolean includeSensitive) {
        if (!member) {
            return q.getGuestVisible() && !q.getIsSensitive();
        }
        return !q.getIsSensitive() || includeSensitive;
    }

    private ActiveSurveyResponse.Prefill prefill(SurveyQuestion q, List<QuestionOption> options,
                                                 HealthProfileSnapshot snapshot) {
        String key = q.getProfileKey();
        if (key == null) {
            return null;
        }
        if (SurveyCodes.PROFILE_KEY_CONDITIONS.equals(key)) {
            if (snapshot.conditions().isEmpty()) {
                return null;
            }
            List<Long> ids = options.stream()
                    .filter(o -> snapshot.conditions().contains(o.getValueCode()))
                    .map(QuestionOption::getOptionId)
                    .toList();
            return ids.isEmpty() ? null : new ActiveSurveyResponse.Prefill(ids, null, null);
        }
        if (SurveyCodes.PROFILE_KEY_MEDICATIONS.equals(key)) {
            if (snapshot.medications().isEmpty()) {
                return null;
            }
            return new ActiveSurveyResponse.Prefill(null, null, medicationsJson(snapshot.medications()));
        }
        Object value = snapshot.value(key);
        if (value == null) {
            return null;
        }
        if (q.getAnswerType() == AnswerType.NUMBER) {
            return new ActiveSurveyResponse.Prefill(null, (BigDecimal) value, null);
        }
        return options.stream()
                .filter(o -> o.getValueCode().equals(value))
                .findFirst()
                .map(o -> new ActiveSurveyResponse.Prefill(List.of(o.getOptionId()), null, null))
                .orElse(null);
    }

    private String medicationsJson(List<MedicationInfo> medications) {
        List<Map<String, String>> items = medications.stream()
                .map(m -> m.drugId() != null ? Map.of("drugId", m.drugId()) : Map.of("name", m.unmatchedName()))
                .toList();
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }

    // 추천 결과 저장 시 FK 연결용
    public SurveyResponse getResponse(Long responseId) {
        return surveyResponseRepository.findById(responseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SURVEY_RESPONSE_NOT_FOUND));
    }

    // 제출된 응답 한 건을 문항 code 기준으로 읽는다 (추천 입력 조립용)
    public SurveyResponseDetail getResponseDetail(Long responseId) {
        SurveyResponse response = surveyResponseRepository.findById(responseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SURVEY_RESPONSE_NOT_FOUND));
        Map<String, List<SurveyAnswer>> byCode = new LinkedHashMap<>();
        for (SurveyAnswer a : surveyAnswerRepository.findDetailByResponseId(responseId)) {
            byCode.computeIfAbsent(a.getQuestion().getCode(), k -> new ArrayList<>()).add(a);
        }
        Map<String, SurveyResponseDetail.AnswerValue> answers = new LinkedHashMap<>();
        byCode.forEach((code, rows) -> answers.put(code, new SurveyResponseDetail.AnswerValue(
                rows.stream().map(SurveyAnswer::getOption).filter(Objects::nonNull)
                        .map(QuestionOption::getValueCode).toList(),
                rows.get(0).getValueNumber(),
                rows.get(0).getValueText())));
        Long userId = response.getUser() == null ? null : response.getUser().getId();
        return new SurveyResponseDetail(response.getResponseId(), userId, response.getRespondentType(),
                response.getIncludesSensitive(), response.getSubmittedAt(), answers);
    }
}
