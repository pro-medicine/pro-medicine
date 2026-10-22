package com.ucamp.app.domain.recommendation;

import com.ucamp.app.domain.recommendation.cache.CacheKeyFactory;
import com.ucamp.app.domain.recommendation.cache.ResultCacheService;
import com.ucamp.app.domain.recommendation.dose.StandardsCache;
import com.ucamp.app.domain.recommendation.pipeline.PipelineRegistry;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationItem;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationResult;
import com.ucamp.app.domain.recommendation.rule.RuleService;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.FakeLlmClient.FakeAnswer;
import com.ucamp.app.infra.llm.LlmOutputSchema;
import com.ucamp.app.infra.llm.LlmRequest;
import com.ucamp.app.infra.llm.LlmResponse;
import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogRepository;
import com.ucamp.app.support.GoldenFiles;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 결과 고정 테스트 (리팩터링 안전망). 버전마다 같은 시나리오를 돌려 아래를 골든 파일과 비교한다.
 * - LLM으로 보낸 요청 전체 (프롬프트 문자열, 스키마, 모델, 토큰 한도, 캐시 여부, 기록용 메타데이터)
 * - 파이프라인 결과 전체 (용량은 BigDecimal 표기 그대로)
 * - llm_call_logs 행, 메트릭 증가분 (단계 이름, 규칙 적중, 캐시 적중)
 */
@SpringBootTest
class PipelineGoldenTest {
    @Autowired
    PipelineRegistry registry;
    @Autowired
    FakeLlmClient fake;
    @Autowired
    MeterRegistry meterRegistry;
    @Autowired
    LlmCallLogRepository logRepository;
    @Autowired
    ResultCacheService resultCacheService;
    @Autowired
    CacheKeyFactory cacheKeyFactory;
    @Autowired
    StandardsCache standardsCache;
    @Autowired
    RuleService ruleService;

    // 나이가 해마다 바뀌지 않게 출생 연도를 현재 연도 기준으로 둔다
    private static final int BIRTH_40 = Year.now().getValue() - 40;

    /** 흡연 + ACE 억제제 + 고혈압 남성 회원, 비타민 C 25mg 섭취 중 */
    private static final RecommendationInput MEMBER = RecommendationInput.builder()
            .respondentType("MEMBER").gender("M").birthYear(BIRTH_40).smoking("ONE_PACK").drinking("HIGH")
            .caffeine("HIGH").dietType("OMNI").sunExposure("LOW").pregnancy("NOT_APPLICABLE")
            .conditions(List.of("HYPERTENSION"))
            .medications(List.of(new RecommendationInput.Medication("D-003", "ACE_INHIBITOR", "에날라프릴"),
                    new RecommendationInput.Medication(null, null, "기타 약")))
            .sleep("POOR").fatigue("HIGH").goals(List.of("FATIGUE", "IMMUNITY"))
            .currentIntake(List.of(new RecommendationInput.Intake("VITAMIN_C", new BigDecimal("25"), "mg")))
            .responseId(7001L).evalRunId("golden")
            .build();

    /** 비건 여성 비회원, 햇빛 적음 (규칙이 적게 걸려 V8에서 작은 모델) */
    private static final RecommendationInput GUEST = RecommendationInput.builder()
            .respondentType("GUEST").gender("F").birthYear(BIRTH_40).dietType("VEGAN").sunExposure("LOW")
            .goals(List.of("SLEEP"))
            .currentIntake(List.of(new RecommendationInput.Intake("VITAMIN_D", new BigDecimal("10"), "mcg")))
            .responseId(7002L).evalRunId("golden")
            .build();

    /** 특이사항 없는 회원 (걸리는 규칙이 없어 V8에서 작은 모델) */
    private static final RecommendationInput SIMPLE = RecommendationInput.builder()
            .respondentType("MEMBER").gender("F").birthYear(BIRTH_40).responseId(7004L).build();

    /** 금지 성분(베타카로틴·칼륨)과 용량이 섞인 답 */
    private static final FakeAnswer MIXED = new FakeAnswer(List.of(
            new FakeAnswer.Item("VITAMIN_C", "비타민 C", "흡연", new BigDecimal("100.0"), "mg"),
            new FakeAnswer.Item("VITAMIN_D", "비타민 D", "햇빛", new BigDecimal("25"), "mcg"),
            new FakeAnswer.Item("BETA_CAROTENE", "베타카로틴", "눈", null, null),
            new FakeAnswer.Item("POTASSIUM", "칼륨", "혈압", new BigDecimal("1000"), "mg"),
            new FakeAnswer.Item("VITAMIN_B12", "비타민 B12", "비건", null, null)), "요약입니다");

    @BeforeEach
    void setUp() {
        fake.reset();
        resultCacheService.clear();
    }

    @AfterEach
    void tearDown() {
        fake.reset();
        resultCacheService.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"V0", "V1", "V2", "V3", "V4", "V6", "V7", "V8", "RULE_ONLY"})
    void 버전별_정상_재요청_실패_비회원(String version) {
        Map<String, Object> golden = new LinkedHashMap<>();
        fake.answerWith(MIXED);
        golden.put("member", scenario(version, MEMBER));
        golden.put("guest", scenario(version, GUEST));
        golden.put("simple", scenario(version, SIMPLE));

        // 첫 답은 형식 위반(모르는 성분 코드), 재요청 답은 정상. V0는 텍스트라 바로 파싱 실패
        AtomicInteger n = new AtomicInteger();
        fake.setResponder(req -> n.getAndIncrement() == 0 ? invalid(req) : fake.render(req, MIXED));
        golden.put("repair", scenario(version, MEMBER));

        fake.setResponder(this::invalid);
        golden.put("failAfterRepair", scenario(version, MEMBER));

        if ("V6".equals(version)) {
            resultCacheService.clear();
            fake.answerWith(MIXED);
            golden.put("cacheMiss", scenario(version, MEMBER));
            golden.put("cacheHit", scenario(version, MEMBER.toBuilder().responseId(7003L).build()));
        }
        GoldenFiles.assertMatches("pipeline/" + version, golden);
    }

    @Test
    void 결과_캐시_키와_기준_데이터_버전() {
        Map<String, Object> golden = new LinkedHashMap<>();
        golden.put("standardsVersion", standardsCache.dataVersion());
        golden.put("rulesVersion", ruleService.dataVersion());
        golden.put("memberKey", cacheKeyFactory.key(MEMBER.toBuilder().birthYear(1980).build(), "V6", "v4.1", "gpt-6.1-sol"));
        golden.put("guestKey", cacheKeyFactory.key(GUEST.toBuilder().birthYear(1990).build(), "V6", "v4.1", "gpt-6.1-sol"));
        GoldenFiles.assertMatches("pipeline/cache-key", golden);
    }

    // ---------- 시나리오 실행과 스냅샷 ----------

    private Map<String, Object> scenario(String version, RecommendationInput input) {
        int before = fake.requests().size();
        Map<String, Double> metricsBefore = metrics();
        RecommendationResult result = registry.get(version).run(input);
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("requests", fake.requests().subList(before, fake.requests().size()).stream().map(this::request).toList());
        s.put("result", result(result));
        s.put("callLogs", logRepository.findAllById(result.llmCallLogIdsOrEmpty()).stream()
                .sorted((a, b) -> Long.compare(a.getLlmCallLogId(), b.getLlmCallLogId())).map(this::callLog).toList());
        s.put("metrics", delta(metricsBefore, metrics()));
        return s;
    }

    private LlmResponse invalid(LlmRequest req) {
        if (req.outputSchema() == null) {
            return fake.textResponse(req, "JSON 없이 답합니다");
        }
        return fake.render(req, new FakeAnswer(List.of(new FakeAnswer.Item("UNKNOWN_CODE", null, "?", null, null)), "x"));
    }

    private Map<String, Object> request(LlmRequest r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("model", r.model());
        m.put("system", r.system());
        m.put("messages", r.messages().stream().map(x -> Map.of("role", x.role(), "text", x.text())).toList());
        m.put("maxTokens", r.maxTokens());
        m.put("temperature", null); // 보내지 않는다 (필드 삭제 전 골든과 같은 모양 유지)
        m.put("schemas", r.outputSchema() == null ? List.of() : List.of(schema(r.outputSchema())));
        m.put("schemaName", r.outputSchema() == null ? null : r.outputSchema().name());
        m.put("promptCache", r.promptCache());
        m.put("callTag", r.callTag());
        m.put("pipelineVersion", r.pipelineVersion());
        m.put("promptVersion", r.promptVersion());
        m.put("responseId", r.responseId());
        m.put("evalRunId", r.evalRunId());
        m.put("attemptNo", r.attemptNo());
        return m;
    }

    private static Map<String, Object> schema(LlmOutputSchema s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", s.name());
        m.put("strict", s.strict());
        m.put("schema", s.schema());
        return m;
    }

    private Map<String, Object> result(RecommendationResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pipelineVersion", r.pipelineVersion());
        m.put("promptVersion", r.promptVersion());
        m.put("model", r.model());
        m.put("parseSuccess", r.parseSuccess());
        m.put("summary", r.summary());
        m.put("fallback", r.fallback());
        m.put("cacheHit", r.cacheHit());
        m.put("failureReason", r.failureReason());
        m.put("llmCallLogCount", r.llmCallLogIdsOrEmpty().size());
        m.put("items", r.items() == null ? null : r.items().stream().map(this::item).toList());
        return m;
    }

    private Map<String, Object> item(RecommendationItem i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ingredient", i.ingredient());
        m.put("reason", i.reason());
        m.put("sortOrder", i.sortOrder());
        m.put("amount", i.amount() == null ? null : i.amount().toString());
        m.put("unit", i.unit());
        m.put("currentIntake", i.currentIntake() == null ? null : i.currentIntake().toString());
        m.put("intakeStatus", i.intakeStatus());
        m.put("doseNote", i.doseNote());
        m.put("excluded", i.excluded());
        m.put("appliedRuleIds", i.appliedRuleIds());
        m.put("warnings", i.warnings());
        return m;
    }

    private Map<String, Object> callLog(LlmCallLog l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("responseId", l.getResponseId());
        m.put("evalRunId", l.getEvalRunId());
        m.put("pipelineVersion", l.getPipelineVersion());
        m.put("callTag", l.getCallTag());
        m.put("model", l.getModel());
        m.put("promptVersion", l.getPromptVersion());
        m.put("inputTokens", l.getInputTokens());
        m.put("outputTokens", l.getOutputTokens());
        m.put("cacheReadTokens", l.getCacheReadTokens());
        m.put("cacheCreationTokens", l.getCacheCreationTokens());
        m.put("attemptNo", l.getAttemptNo());
        m.put("outcome", l.getOutcome());
        m.put("httpStatus", l.getHttpStatus());
        return m;
    }

    // recommendation.* 메트릭의 (이름 + 태그) → 횟수
    private Map<String, Double> metrics() {
        Map<String, Double> m = new TreeMap<>();
        for (Meter meter : meterRegistry.getMeters()) {
            String name = meter.getId().getName();
            if (!name.startsWith("recommendation.")) {
                continue;
            }
            String key = name + meter.getId().getTags().stream().map(Tag::toString).sorted()
                    .collect(Collectors.joining(",", "{", "}"));
            if (meter instanceof Timer t) {
                m.put(key, (double) t.count());
            } else if (meter instanceof Counter c) {
                m.put(key, c.count());
            }
        }
        return m;
    }

    private static Map<String, Double> delta(Map<String, Double> before, Map<String, Double> after) {
        Map<String, Double> d = new TreeMap<>();
        after.forEach((k, v) -> {
            double diff = v - before.getOrDefault(k, 0.0);
            if (diff != 0) {
                d.put(k, diff);
            }
        });
        return d;
    }
}
