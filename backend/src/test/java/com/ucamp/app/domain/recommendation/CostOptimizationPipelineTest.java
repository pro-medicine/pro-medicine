package com.ucamp.app.domain.recommendation;

import com.ucamp.app.domain.recommendation.cache.CacheKeyFactory;
import com.ucamp.app.domain.recommendation.cache.ResultCacheService;
import com.ucamp.app.domain.recommendation.pipeline.ModelRouter;
import com.ucamp.app.domain.recommendation.pipeline.PipelineRegistry;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationItem;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationResult;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.FakeLlmClient.FakeAnswer;
import com.ucamp.app.infra.llm.LlmRequest;
import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// STEP 08: V6 결과 캐시, V7 프롬프트 캐싱, V8 모델 라우팅
@SpringBootTest
class CostOptimizationPipelineTest {
    @Autowired
    PipelineRegistry registry;
    @Autowired
    FakeLlmClient fake;
    @Autowired
    ResultCacheService cache;
    @Autowired
    CacheKeyFactory keyFactory;
    @Autowired
    ModelRouter router;
    @Autowired
    LlmCallLogRepository logRepository;
    @Autowired
    MeterRegistry meterRegistry;

    private static RecommendationInput smoker(String vitaminCIntake, Long responseId) {
        return RecommendationInput.builder()
                .respondentType("MEMBER").gender("M").birthYear(1990).smoking("ONE_PACK").conditions(List.of())
                .medications(List.of()).goals(List.of("IMMUNITY", "SLEEP"))
                .currentIntake(List.of(new RecommendationInput.Intake("VITAMIN_C", new BigDecimal(vitaminCIntake), "mg")))
                .responseId(responseId)
                .build();
    }

    @BeforeEach
    void setUp() {
        fake.reset();
        cache.clear();
        fake.answerWith(new FakeAnswer(List.of(new FakeAnswer.Item("VITAMIN_C", null, "흡연", null, null),
                new FakeAnswer.Item("BETA_CAROTENE", null, "x", null, null)), "요약"));
    }

    @AfterEach
    void reset() {
        fake.reset();
        cache.clear();
    }

    @Test
    void V6_같은_정규화_입력이면_LLM을_다시_부르지_않고_용량과_규칙은_다시_계산() {
        RecommendationResult first = registry.get("V6").run(smoker("25", 1L));
        RecommendationResult second = registry.get("V6").run(smoker("100", 2L)); // 응답 ID·함량만 다름

        assertThat(fake.requests()).hasSize(1);
        assertThat(first.cacheHit()).isFalse();
        assertThat(second.cacheHit()).isTrue();
        assertThat(second.llmCallLogIdsOrEmpty()).isEmpty();
        // 용량은 각자의 현재 섭취량으로 다시 계산 (125 - 25, 125 - 100)
        assertThat(first.finalItems().get(0).amount()).isEqualByComparingTo("100");
        assertThat(second.finalItems().get(0).amount()).isEqualByComparingTo("25");
        // 규칙도 다시 적용 (베타카로틴 제외)
        assertThat(second.itemsOrEmpty()).filteredOn(RecommendationItem::excluded)
                .extracting(RecommendationItem::ingredient).containsExactly("BETA_CAROTENE");
        assertThat(meterRegistry.find(ResultCacheService.METRIC).tag("result", "hit").counter().count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void V6_추천에_영향을_주는_값이_다르면_캐시_미스() {
        registry.get("V6").run(smoker("25", 1L));
        registry.get("V6").run(smoker("25", 1L).toBuilder().sunExposure("LOW").build());

        assertThat(fake.requests()).hasSize(2);
    }

    @Test
    void 캐시_키는_목록_순서_응답ID_섭취_함량과_무관하고_버전이_다르면_달라진다() {
        RecommendationInput a = smoker("25", 1L).toBuilder().goals(List.of("SLEEP", "IMMUNITY")).evalRunId("e1").build();
        RecommendationInput b = smoker("80", 99L);

        assertThat(keyFactory.key(a, "V6", "v4.1", "m")).isEqualTo(keyFactory.key(b, "V6", "v4.1", "m"));
        assertThat(keyFactory.key(a, "V6", "v4.1", "m")).isNotEqualTo(keyFactory.key(a, "V6", "v4.2", "m"));
        assertThat(keyFactory.key(a, "V6", "v4.1", "m")).isNotEqualTo(keyFactory.key(a, "V6", "v4.1", "small"));
        assertThat(keyFactory.key(a, "V6", "v4.1", "m")).hasSize(64).doesNotContain("SMOKING");
        assertThat(CacheKeyFactory.KEY_FIELDS).doesNotContain("responseId", "userId", "evalRunId", "height", "weight");
    }

    @Test
    void V6_파싱에_실패한_결과는_캐시하지_않는다() {
        fake.setResponder(req -> fake.textResponse(req, "도구 없이 답함"));
        registry.get("V6").run(smoker("25", 1L));
        fake.answerWith(new FakeAnswer(List.of(new FakeAnswer.Item("VITAMIN_C", null, "흡연", null, null)), "요약"));

        RecommendationResult r = registry.get("V6").run(smoker("25", 1L));

        assertThat(r.cacheHit()).isFalse();
        assertThat(r.parseSuccess()).isTrue();
    }

    @Test
    void V7_고정_부분에_캐시_지점을_표시하고_결과_캐시는_쓰지_않는다() {
        registry.get("V7").run(smoker("25", 1L));
        registry.get("V7").run(smoker("25", 2L));

        assertThat(fake.requests()).hasSize(2);
        LlmRequest req = fake.requests().get(0);
        assertThat(req.cacheControl()).isTrue();
        // 금지 목록(사용자마다 다름)은 시스템이 아니라 사용자 메시지에 있다 → 캐시 지점 뒤
        assertThat(req.system()).doesNotContain("다음 성분은 추천하지 마라");
        assertThat(req.messages().get(0).text()).contains("다음 성분은 추천하지 마라");
        int prefixChars = req.system().length() + String.valueOf(req.toolsOrEmpty().get(0).inputSchema()).length();
        System.out.println("[V7] fixed prefix chars = " + prefixChars + " (system " + req.system().length() + ")");
        List<LlmCallLog> logs = logRepository.findByResponseIdOrderByLlmCallLogIdAsc(2L);
        LlmCallLog second = logs.get(logs.size() - 1);
        if (prefixChars / 3 >= 512) {
            assertThat(second.getCacheReadTokens()).isPositive();
        } else {
            assertThat(second.getCacheReadTokens()).isZero();
        }
    }

    @Test
    void V8_입력_복잡도로_모델을_고른다() {
        RecommendationInput simple = smoker("25", 1L);
        RecommendationInput withDrug = simple.toBuilder()
                .medications(List.of(new RecommendationInput.Medication("D-007", "WARFARIN", "와파린"))).build();
        RecommendationInput withCondition = simple.toBuilder().conditions(List.of("DIABETES")).build();
        // 걸리는 규칙 3개 이상: 흡연(R-01, R-02) + 비건(R-05)
        RecommendationInput manyRules = simple.toBuilder().dietType("VEGAN").build();

        assertThat(router.isComplex(simple)).isFalse();
        assertThat(router.isComplex(withDrug)).isTrue();
        assertThat(router.isComplex(withCondition)).isTrue();
        assertThat(router.isComplex(manyRules)).isTrue();

        registry.get("V8").run(simple);
        registry.get("V8").run(withDrug);
        assertThat(fake.requests().get(0).model()).isEqualTo("claude-haiku-5-5");
        assertThat(fake.requests().get(1).model()).isEqualTo("claude-opus-5-5");
        assertThat(fake.requests().get(1).cacheControl()).isTrue();
    }
}
