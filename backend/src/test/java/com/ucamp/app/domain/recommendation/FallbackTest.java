package com.ucamp.app.domain.recommendation;

import com.ucamp.app.domain.recommendation.dto.RecommendationResponse;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationItem;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationResult;
import com.ucamp.app.domain.recommendation.pipeline.RuleOnlyPipeline;
import com.ucamp.app.domain.recommendation.service.RecommendationService;
import com.ucamp.app.domain.survey.dto.SubmitSurveyRequest;
import com.ucamp.app.domain.survey.service.SurveySubmitService;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.LlmCallException;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static com.ucamp.app.support.SurveyFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

// STEP 10: 재시도 소진·서킷 OPEN이면 규칙 기반 폴백 (V10 설정: llm.resilience.enabled=true)
@SpringBootTest(properties = {"llm.resilience.enabled=true", "llm.resilience.max-attempts=2",
        "llm.resilience.initial-backoff=10ms", "recommendation.pipeline-version=V4"})
class FallbackTest {
    @Autowired
    RecommendationService recommendationService;
    @Autowired
    SurveySubmitService surveySubmitService;
    @Autowired
    RuleOnlyPipeline ruleOnlyPipeline;
    @Autowired
    FakeLlmClient fake;
    @Autowired
    MeterRegistry meterRegistry;

    @AfterEach
    void reset() {
        fake.reset();
    }

    @Test
    void 재시도_소진이면_RULE_ONLY_폴백으로_응답() {
        fake.setResponder(req -> {
            throw new LlmCallException(LlmCallException.Kind.HTTP_ERROR, 503, null, 1, "x", null);
        });
        long responseId = surveySubmitService.submit(SURVEY_ID, null, new SubmitSurveyRequest(List.of(
                new SubmitSurveyRequest.Answer(Q_GENDER, List.of(GENDER_M), null, null),
                new SubmitSurveyRequest.Answer(Q_BIRTH, null, BigDecimal.valueOf(1990), null),
                new SubmitSurveyRequest.Answer(Q_SMOKING, List.of(SMOKING_ONE_PACK), null, null),
                new SubmitSurveyRequest.Answer(Q_SUN, List.of(SUN_LOW), null, null)))).responseId();

        RecommendationResponse r = recommendationService.create(responseId);

        assertThat(fake.requests()).hasSize(2); // 재시도 1회 포함
        assertThat(r.fallback()).isTrue();
        assertThat(r.modelName()).isEqualTo("RULE_ONLY");
        assertThat(r.notice()).isEqualTo(RuleOnlyPipeline.NOTICE);
        assertThat(r.items()).extracting(RecommendationResponse.Item::ingredient)
                .containsExactly("VITAMIN_C", "VITAMIN_D");
        assertThat(r.items().get(0).amount()).isEqualByComparingTo("125");
        assertThat(meterRegistry.find("recommendation.fallback").counter().count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void 규칙_기반_추천은_ADJUST와_R05_R06_대상만_ASK는_경고로만() {
        RecommendationInput in = RecommendationInput.builder()
                .respondentType("MEMBER").gender("M").birthYear(1990).smoking("HALF_PACK").dietType("VEGAN")
                .caffeine("HIGH")
                .currentIntake(List.of(new RecommendationInput.Intake("IRON", BigDecimal.TEN, "mg")))
                .build();

        RecommendationResult r = ruleOnlyPipeline.run(in);

        assertThat(r.fallback()).isTrue();
        assertThat(r.finalItems()).extracting(RecommendationItem::ingredient)
                .containsExactly("VITAMIN_C", "VITAMIN_B12"); // R-01 ADJUST, R-05. R-23(ASK 철분)·R-04(TIMING)은 추천 아님
        assertThat(r.llmCallLogIdsOrEmpty()).isEmpty();
    }

    @Test
    void 비회원_규칙_기반_추천도_금지_성분은_없다() {
        RecommendationResult r = ruleOnlyPipeline.run(RecommendationInput.builder()
                .respondentType("GUEST").gender("F").birthYear(1995).sunExposure("LOW").build());

        assertThat(r.finalItems()).extracting(RecommendationItem::ingredient).containsExactly("VITAMIN_D");
    }
}
