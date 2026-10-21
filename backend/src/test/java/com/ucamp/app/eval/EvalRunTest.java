package com.ucamp.app.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucamp.app.domain.health.entity.Drug;
import com.ucamp.app.domain.health.repository.DrugRepository;
import com.ucamp.app.domain.recommendation.pipeline.PipelineRegistry;
import com.ucamp.app.eval.EvalCaseFile.EvalCase;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.LlmProperties;
import com.ucamp.app.infra.llm.log.LlmCallLogRepository;
import com.ucamp.app.global.metrics.StageTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 평가 실행 진입점. ./gradlew evalRun -PpipelineVersion=V0 [-Pcases=N] [-Prepeats=R] [-Pmode=repro]
 *   [-Pllm=anthropic|fake|fake-noisy] [-Pconfirm=true] [-Pmodel=...] [-Plabel=...] [-Pnote=...]
 * - 실제 API(-Pllm=anthropic, 기본)는 -Pconfirm=true가 없으면 예상 호출 수·비용만 출력하고 끝낸다
 * - 결과: docs/perf/results.csv 한 줄 + docs/perf/runs/{eval_run_id}.json
 * - Fake로 돌린 결과는 label에 fake가 붙는다 (실제 측정값이 아님)
 */
@Tag("eval")
@SpringBootTest
class EvalRunTest {
    private static final Path CSV = Path.of("docs/perf/results.csv");
    private static final Path RUNS = Path.of("docs/perf/runs");
    // 실행 전 예상 비용 계산용 대략값 (thinking 포함 출력). 실제 값은 실행 후 llm_call_logs로 계산한다
    private static final int EST_INPUT_TOKENS = 2000;
    private static final int EST_OUTPUT_TOKENS = 2000;

    @Autowired
    PipelineRegistry pipelineRegistry;
    @Autowired
    DrugRepository drugRepository;
    @Autowired
    FakeLlmClient fakeLlmClient;
    @Autowired
    LlmCallLogRepository logRepository;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    LlmProperties llmProperties;
    @Autowired
    ApplicationContext context;
    @Autowired
    MeterRegistry meterRegistry;

    @DynamicPropertySource
    static void llmProvider(DynamicPropertyRegistry registry) {
        String llm = System.getProperty("eval.llm", "anthropic");
        registry.add("llm.provider", () -> "anthropic".equalsIgnoreCase(llm) ? "anthropic" : "fake");
        String model = System.getProperty("eval.model");
        if (model != null && !model.isBlank()) {
            registry.add("llm.model", () -> model);
        }
    }

    @Test
    void evalRun() throws Exception {
        String version = System.getProperty("eval.pipelineVersion", "V0");
        boolean repro = "repro".equalsIgnoreCase(System.getProperty("eval.mode", "full"));
        int caseLimit = Integer.parseInt(System.getProperty("eval.cases", "0"));
        int repeats = repro ? 5 : Integer.parseInt(System.getProperty("eval.repeats", "1"));
        EvalRunner.LlmMode mode = switch (System.getProperty("eval.llm", "anthropic").toLowerCase()) {
            case "fake" -> EvalRunner.LlmMode.FAKE;
            case "fake-noisy" -> EvalRunner.LlmMode.FAKE_NOISY;
            default -> EvalRunner.LlmMode.ANTHROPIC;
        };
        boolean confirm = Boolean.parseBoolean(System.getProperty("eval.confirm", "false"));

        EvalCaseFile file = EvalRunner.loadCases(objectMapper);
        List<EvalCase> cases = EvalRunner.select(file, repro, caseLimit);
        int runs = cases.size() * repeats;
        System.out.printf("[EVAL] version=%s mode=%s llm=%s model=%s cases=%d repeats=%d reviewed=%s%n",
                version, repro ? "repro" : "full", mode, llmProperties.getModel(), cases.size(), repeats,
                file.meta().reviewed());

        // 규칙 기반 폴백(RULE_ONLY)은 LLM을 부르지 않으므로 비용·confirm 없이 실제 측정한다
        boolean llmFree = "RULE_ONLY".equals(version);
        if (llmFree) {
            mode = EvalRunner.LlmMode.FAKE;
        }
        if (mode == EvalRunner.LlmMode.ANTHROPIC) {
            int maxCalls = "V0".equals(version) ? runs : runs * 2;
            EvalCost.Price price = EvalCost.price(llmProperties.getModel());
            String cost = price == null ? "단가 미등록 모델"
                    : String.format("약 $%.2f ~ $%.2f (호출당 입력 %d·출력 %d 토큰 가정)",
                    runs * (EST_INPUT_TOKENS * price.input() + EST_OUTPUT_TOKENS * price.output()) / 1_000_000.0,
                    maxCalls * (EST_INPUT_TOKENS * price.input() + EST_OUTPUT_TOKENS * price.output()) / 1_000_000.0,
                    EST_INPUT_TOKENS, EST_OUTPUT_TOKENS);
            System.out.printf("[EVAL] 예상 LLM 호출 수: %d ~ %d (재요청 포함 최대), 예상 비용: %s%n", runs, maxCalls, cost);
            if (!confirm) {
                System.out.println("[EVAL] -Pconfirm=true 가 없어 실제 API를 호출하지 않고 끝냅니다.");
                return;
            }
        }

        // V6 결과 캐시처럼 같은 평가셋을 연속으로 돌려야 하는 경우 -Ppasses=2 (같은 JVM이라 캐시가 유지된다)
        int passes = Integer.parseInt(System.getProperty("eval.passes", "1"));
        for (int pass = 1; pass <= passes; pass++) {
            runPass(version, repro, caseLimit, repeats, mode, file, cases, passes > 1 ? pass : 0, llmFree);
        }
    }

    private void runPass(String version, boolean repro, int caseLimit, int repeats, EvalRunner.LlmMode mode,
                         EvalCaseFile file, List<EvalCase> cases, int pass, boolean llmFree) throws Exception {
        String passSuffix = pass > 0 ? "-pass" + pass : "";
        String evalRunId = version + "-" + (repro ? "repro-" : "") + mode.name().toLowerCase().replace('_', '-')
                + passSuffix + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Map<String, Drug> drugs = drugRepository.findAll().stream()
                .collect(Collectors.toMap(Drug::getDrugId, Function.identity()));
        EvalRunner runner = new EvalRunner(pipelineRegistry, drugs, fakeLlmClient, logRepository,
                IngredientAliases.load(objectMapper), objectMapper, EvalHooks.overLimitChecker(context),
                EvalHooks.routedModel(context, version));
        EvalRunner.Summary summary = runner.run(
                new EvalRunner.Config(version, repro, caseLimit, repeats, mode, evalRunId), cases);

        String label = version + (repro ? "-repro" : "")
                + (llmFree ? "" : mode == EvalRunner.LlmMode.FAKE ? "-fake" : mode == EvalRunner.LlmMode.FAKE_NOISY ? "-fake-noisy" : "")
                + passSuffix
                + (System.getProperty("eval.label") == null ? "" : "-" + System.getProperty("eval.label"));
        String note = buildNote(file, summary, llmFree ? "LLM 미사용(규칙 기반 폴백) - 실제 측정값" : System.getProperty("eval.note"), llmFree);
        String step = System.getProperty("eval.step", stepOf(version));
        EvalRunner.appendCsv(CSV, EvalRunner.csvRow(step, label, summary, note));
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("label", label);
        extra.put("step", step);
        extra.put("evalSetReviewed", file.meta().reviewed());
        extra.put("stageTimings", stageTimings(version));
        extra.put("ruleHits", ruleHits());
        extra.put("cacheCounters", cacheCounters());
        Path json = EvalRunner.writeRunJson(RUNS, summary, extra, objectMapper);
        System.out.printf("[EVAL] label=%s metrics=%s%n", label, summary.metrics());
        System.out.printf("[EVAL] llmCalls=%d repair=%d errors=%d failures=%s byModel=%s recallByDifficulty=%s%n",
                summary.llmCalls(), summary.repairCalls(), summary.llmErrors(), summary.failureReasons(),
                summary.callsByModel(), summary.recallByDifficulty());
        System.out.println("[EVAL] stages=" + extra.get("stageTimings"));
        System.out.println("[EVAL] ruleHits=" + extra.get("ruleHits"));
        System.out.println("[EVAL] saved " + CSV + " and " + json);
    }

    // 이번 실행에서 기록된 단계별 Timer (ms). 같은 JVM에서 한 버전만 돌리므로 그 버전 값이다
    private Map<String, Map<String, Object>> stageTimings(String version) {
        Map<String, Map<String, Object>> out = new java.util.TreeMap<>();
        for (Timer t : meterRegistry.find(StageTimer.STAGE_METRIC).tag("pipeline_version", version).timers()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("count", t.count());
            m.put("mean_ms", t.mean(TimeUnit.MILLISECONDS));
            m.put("max_ms", t.max(TimeUnit.MILLISECONDS));
            for (ValueAtPercentile v : t.takeSnapshot().percentileValues()) {
                m.put("p" + Math.round(v.percentile() * 100) + "_ms", v.value(TimeUnit.MILLISECONDS));
            }
            out.put(t.getId().getTag("stage"), m);
        }
        return out;
    }

    // 규칙 적중 횟수 (phase:rule_id → 횟수, 많은 순). 같은 JVM에서 한 버전만 돌리므로 이번 실행 값이다
    private Map<String, Long> ruleHits() {
        Map<String, Long> hits = new LinkedHashMap<>();
        meterRegistry.find("recommendation.rule.hit").counters().stream()
                .sorted((a, b) -> Double.compare(b.count(), a.count()))
                .forEach(c -> hits.put(c.getId().getTag("phase") + ":" + c.getId().getTag("rule_id"), (long) c.count()));
        return hits;
    }

    private Map<String, Double> cacheCounters() {
        Map<String, Double> m = new LinkedHashMap<>();
        meterRegistry.find("recommendation.cache").counters()
                .forEach(c -> m.put(c.getId().getTag("result"), c.count()));
        return m;
    }

    private static String buildNote(EvalCaseFile file, EvalRunner.Summary s, String extra, boolean llmFree) {
        StringBuilder sb = new StringBuilder("evalRunId=").append(s.config().evalRunId())
                .append("; reviewed=").append(file.meta().reviewed())
                .append("; dose_n=").append(s.metrics().doseN())
                .append("; repair_calls=").append(s.repairCalls());
        if (!s.callsByModel().isEmpty() && s.callsByModel().size() > 1) {
            sb.append("; calls_by_model=").append(s.callsByModel());
        }
        if (!s.recallByModelGroup().isEmpty()) {
            sb.append("; recall_by_routed_model=").append(s.recallByModelGroup());
        }
        if (s.avgCacheCreationTokens() != null && s.avgCacheCreationTokens() > 0) {
            sb.append("; avg_cache_creation_tokens=").append(Math.round(s.avgCacheCreationTokens()));
        }
        if (s.llmErrors() > 0) {
            sb.append("; llm_errors=").append(s.llmErrors());
        }
        if (!llmFree && s.config().llmMode() != EvalRunner.LlmMode.ANTHROPIC) {
            sb.append("; FAKE LLM - 러너 검증용, 실제 측정값 아님 (토큰은 글자 수 추정치)");
        }
        if (extra != null && !extra.isBlank()) {
            sb.append("; ").append(extra);
        }
        return sb.toString();
    }

    static String stepOf(String version) {
        return switch (version) {
            case "V0" -> "04";
            case "V1" -> "05";
            case "V2" -> "06";
            case "V3", "V4" -> "07";
            case "V6", "V7", "V8" -> "08";
            default -> "10";
        };
    }
}
