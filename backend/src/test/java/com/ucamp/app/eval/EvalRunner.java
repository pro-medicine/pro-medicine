package com.ucamp.app.eval;

import tools.jackson.databind.ObjectMapper;
import com.ucamp.app.domain.health.entity.Drug;
import com.ucamp.app.domain.recommendation.pipeline.PipelineRegistry;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationItem;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationPipeline;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationResult;
import com.ucamp.app.eval.EvalCaseFile.EvalCase;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.LlmCallException;
import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogRepository;
import com.ucamp.app.support.PerfStats;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 평가 러너. 케이스를 정규화 입력으로 바꿔 지정한 버전의 파이프라인을 직접 실행하고(HTTP 없음),
 * 품질 지표(EvalMetrics) + llm_call_logs 집계(토큰·지연·비용)를 계산한다.
 * 결과 저장(results.csv, runs/*.json)은 EvalRunTest가 한다.
 */
public class EvalRunner {

    public enum LlmMode {OPENAI, FAKE, FAKE_NOISY}

    /** 평가 실행 조건. caseLimit = 0이면 전체 */
    public record Config(String pipelineVersion, boolean repro, int caseLimit, int repeats, LlmMode llmMode,
                         String evalRunId) {
    }

    /** 결과 항목의 상한 초과 여부 (모르면 null). STEP 06부터 기준표로 채운다 */
    public interface OverLimitChecker {
        Boolean isOverLimit(RecommendationInput input, RecommendationItem item);
    }

    public record RunDetail(String caseId, int repeat, String difficulty, boolean parseSuccess, String failureReason,
                            boolean llmError, boolean cacheHit, boolean fallback, List<String> finalCodes,
                            List<String> rawCodes, List<String> excludedCodes, Map<String, String> doses,
                            double e2eMs, List<Long> llmCallLogIds) {
    }

    public record Summary(Config config, String model, int cases, int repeats, EvalMetrics.Result metrics,
                          Double llmP50, Double llmP95, Double e2eP95, Double avgInputTokens,
                          Double avgOutputTokens, Double avgCacheReadTokens, Double avgCacheCreationTokens,
                          Double costPerReq, Double cacheHitRate, int llmCalls, int repairCalls, int llmErrors,
                          Map<String, Long> callsByModel, Map<String, Long> failureReasons,
                          Map<String, Double> recallByDifficulty, Map<String, Double> recallByModelGroup,
                          List<RunDetail> details) {
    }

    private final PipelineRegistry pipelineRegistry;
    private final Map<String, Drug> drugs;
    private final FakeLlmClient fakeLlmClient;
    private final LlmCallLogRepository logRepository;
    private final IngredientAliases aliases;
    private final ObjectMapper objectMapper;
    private final OverLimitChecker overLimitChecker;
    private final Function<RecommendationInput, String> routedModel;

    public EvalRunner(PipelineRegistry pipelineRegistry, Map<String, Drug> drugs, FakeLlmClient fakeLlmClient,
                      LlmCallLogRepository logRepository, IngredientAliases aliases, ObjectMapper objectMapper,
                      OverLimitChecker overLimitChecker, Function<RecommendationInput, String> routedModel) {
        this.pipelineRegistry = pipelineRegistry;
        this.drugs = drugs;
        this.fakeLlmClient = fakeLlmClient;
        this.logRepository = logRepository;
        this.aliases = aliases;
        this.objectMapper = objectMapper;
        this.overLimitChecker = overLimitChecker;
        this.routedModel = routedModel;
    }

    public static EvalCaseFile loadCases(ObjectMapper objectMapper) {
        try (InputStream in = EvalRunner.class.getResourceAsStream("/eval/cases.json")) {
            return objectMapper.readValue(in, EvalCaseFile.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<EvalCase> select(EvalCaseFile file, boolean repro, int caseLimit) {
        List<EvalCase> cases = file.cases();
        if (repro) {
            cases = cases.stream().filter(c -> file.meta().reproCaseIds().contains(c.caseId())).toList();
        }
        if (caseLimit > 0 && caseLimit < cases.size()) {
            cases = cases.subList(0, caseLimit);
        }
        return cases;
    }

    public Summary run(Config config, List<EvalCase> cases) {
        RecommendationPipeline pipeline = pipelineRegistry.get(config.pipelineVersion());
        List<EvalMetrics.Run> runs = new ArrayList<>();
        List<RunDetail> details = new ArrayList<>();
        PerfStats e2e = new PerfStats();
        Map<String, List<Double>> recallGroups = new TreeMap<>();
        try {
            for (EvalCase c : cases) {
                for (int rep = 0; rep < config.repeats(); rep++) {
                    configureFake(config, c, rep);
                    RecommendationInput input = EvalInputMapper.toInput(c, drugs, config.evalRunId());
                    long start = System.nanoTime();
                    RecommendationResult result;
                    boolean llmError = false;
                    try {
                        result = pipeline.run(input);
                    } catch (LlmCallException e) {
                        llmError = true;
                        result = RecommendationResult.builder().pipelineVersion(config.pipelineVersion())
                                .parseSuccess(false).items(List.of()).failureReason("LLM_" + e.getKind())
                                .llmCallLogIds(e.getCallLogId() == null ? List.of() : List.of(e.getCallLogId()))
                                .build();
                    }
                    double ms = (System.nanoTime() - start) / 1_000_000.0;
                    e2e.add(ms);

                    List<EvalMetrics.Item> finalItems = toItems(result.finalItems());
                    List<EvalMetrics.Item> rawItems = toItems(result.itemsOrEmpty());
                    List<Boolean> overLimit = overLimitChecker == null ? null
                            : result.finalItems().stream().map(i -> overLimitChecker.isOverLimit(input, i)).toList();
                    EvalMetrics.Run run = new EvalMetrics.Run(c, rep, result.parseSuccess(), finalItems, rawItems, overLimit);
                    runs.add(run);
                    if (!c.expected().mustInclude().isEmpty()) {
                        double recall = (double) c.expected().mustInclude().stream()
                                .filter(EvalMetrics.keys(finalItems)::contains).count() / c.expected().mustInclude().size();
                        recallGroups.computeIfAbsent("difficulty:" + c.difficulty(), k -> new ArrayList<>()).add(recall);
                        if (routedModel != null) {
                            recallGroups.computeIfAbsent("model:" + routedModel.apply(input), k -> new ArrayList<>()).add(recall);
                        }
                    }
                    details.add(new RunDetail(c.caseId(), rep, c.difficulty(), result.parseSuccess(),
                            result.failureReason(), llmError, result.cacheHit(), result.fallback(),
                            finalItems.stream().map(EvalMetrics.Item::key).toList(),
                            rawItems.stream().map(EvalMetrics.Item::key).toList(),
                            result.itemsOrEmpty().stream().filter(RecommendationItem::excluded)
                                    .map(RecommendationItem::ingredient).toList(),
                            doses(finalItems), PerfStats.round(ms), result.llmCallLogIdsOrEmpty()));
                }
            }
        } finally {
            fakeLlmClient.reset();
        }

        EvalMetrics.Result metrics = EvalMetrics.compute(runs, config.repro());
        List<LlmCallLog> logs = logRepository.findByEvalRunIdOrderByLlmCallLogIdAsc(config.evalRunId());
        PerfStats llm = new PerfStats();
        logs.forEach(l -> llm.add(l.getLatencyMs()));
        int n = runs.size();
        long in = sum(logs, LlmCallLog::getInputTokens);
        long out = sum(logs, LlmCallLog::getOutputTokens);
        long cacheRead = sum(logs, LlmCallLog::getCacheReadTokens);
        long cacheWrite = sum(logs, LlmCallLog::getCacheCreationTokens);
        Double totalCost = 0.0;
        for (LlmCallLog l : logs) {
            Double c = EvalCost.cost(l.getModel(), nz(l.getInputTokens()), nz(l.getCacheCreationTokens()),
                    nz(l.getCacheReadTokens()), nz(l.getOutputTokens()));
            if (c == null) {
                totalCost = null;
                break;
            }
            totalCost += c;
        }
        long cacheHits = details.stream().filter(RunDetail::cacheHit).count();
        boolean cacheVersion = config.pipelineVersion().startsWith("V6");
        Map<String, Long> byModel = logs.stream()
                .collect(Collectors.groupingBy(LlmCallLog::getModel, TreeMap::new, Collectors.counting()));
        Map<String, Long> reasons = details.stream().filter(d -> d.failureReason() != null)
                .collect(Collectors.groupingBy(RunDetail::failureReason, TreeMap::new, Collectors.counting()));
        Map<String, Double> recallByDifficulty = new TreeMap<>();
        Map<String, Double> recallByModel = new TreeMap<>();
        recallGroups.forEach((k, v) -> {
            double avg = v.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            if (k.startsWith("difficulty:")) {
                recallByDifficulty.put(k.substring(11), avg);
            } else {
                recallByModel.put(k.substring(6), avg);
            }
        });
        String model = logs.isEmpty() ? "-" : byModel.size() == 1 ? byModel.keySet().iterator().next() : "mixed";

        return new Summary(config, model, cases.size(), config.repeats(), metrics,
                logs.isEmpty() ? null : llm.percentile(50), logs.isEmpty() ? null : llm.percentile(95),
                e2e.percentile(95),
                n == 0 ? null : (double) in / n, n == 0 ? null : (double) out / n,
                n == 0 ? null : (double) cacheRead / n, n == 0 ? null : (double) cacheWrite / n,
                totalCost == null || n == 0 || logs.isEmpty() ? null : totalCost / n,
                cacheVersion && n > 0 ? (double) cacheHits / n : null,
                logs.size(), (int) logs.stream().filter(l -> "RECOMMEND_REPAIR".equals(l.getCallTag())).count(),
                (int) details.stream().filter(RunDetail::llmError).count(),
                byModel, reasons, recallByDifficulty, recallByModel, details);
    }

    private void configureFake(Config config, EvalCase c, int rep) {
        switch (config.llmMode()) {
            case FAKE -> fakeLlmClient.answerWith(FakeScenarios.oracle(c, aliases));
            case FAKE_NOISY -> fakeLlmClient.setResponder(FakeScenarios.noisy(c, aliases, fakeLlmClient, objectMapper,
                    (c.caseId() + "#" + rep + "#" + config.pipelineVersion().charAt(0)).hashCode()));
            case OPENAI -> {
            }
        }
    }

    private List<EvalMetrics.Item> toItems(List<RecommendationItem> items) {
        return items.stream()
                .map(i -> new EvalMetrics.Item(aliases.toCode(i.ingredient()), i.ingredient(), i.amount(), i.unit()))
                .toList();
    }

    private static Map<String, String> doses(List<EvalMetrics.Item> items) {
        Map<String, String> m = new LinkedHashMap<>();
        for (EvalMetrics.Item i : items) {
            if (i.amount() != null) {
                m.putIfAbsent(i.key(), i.amount().stripTrailingZeros().toPlainString() + " " + i.unit());
            }
        }
        return m;
    }

    private static long sum(List<LlmCallLog> logs, Function<LlmCallLog, Integer> f) {
        return logs.stream().map(f).mapToLong(EvalRunner::nz).sum();
    }

    private static long nz(Integer v) {
        return v == null ? 0 : v;
    }

    // ---------- 저장 ----------

    public static final String CSV_HEADER = "step,label,date,model,cases,repeats,recall,precision,violation_rate,"
            + "raw_violation_rate,over_limit_rate,dose_error_rate,reproducibility_set,reproducibility_dose,"
            + "parse_fail_rate,llm_p50_ms,llm_p95_ms,e2e_p95_ms,avg_input_tokens,avg_output_tokens,"
            + "avg_cache_read_tokens,cost_per_req_usd,cache_hit_rate,tps,error_rate,note";

    public static void appendCsv(Path csv, List<Object> values) throws IOException {
        Files.createDirectories(csv.getParent());
        if (!Files.exists(csv)) {
            Files.writeString(csv, CSV_HEADER + "\n");
        }
        String line = values.stream().map(EvalRunner::csvCell).collect(Collectors.joining(","));
        Files.writeString(csv, line + "\n", java.nio.file.StandardOpenOption.APPEND);
    }

    static String csvCell(Object v) {
        if (v == null) {
            return "";
        }
        String s;
        if (v instanceof Double d) {
            s = d.isNaN() ? "" : String.valueOf(Math.round(d * 10000.0) / 10000.0);
        } else {
            s = v.toString();
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    public static List<Object> csvRow(String step, String label, Summary s, String note) {
        EvalMetrics.Result m = s.metrics();
        return List.of(step, label, LocalDate.now().toString(), s.model(), s.cases(), s.repeats(),
                opt(m.recall()), opt(m.precision()), opt(m.violationRate()), opt(m.rawViolationRate()),
                opt(m.overLimitRate()), opt(m.doseErrorRate()), opt(m.reproducibilitySet()), opt(m.reproducibilityDose()),
                opt(m.parseFailRate()), opt(s.llmP50()), opt(s.llmP95()), opt(s.e2eP95()),
                opt(s.avgInputTokens()), opt(s.avgOutputTokens()), opt(s.avgCacheReadTokens()),
                opt(s.costPerReq()), opt(s.cacheHitRate()), "", "", note == null ? "" : note);
    }

    private static Object opt(Double d) {
        return d == null ? "" : d;
    }

    public static Path writeRunJson(Path dir, Summary s, Map<String, Object> extra, ObjectMapper objectMapper)
            throws IOException {
        Files.createDirectories(dir);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("evalRunId", s.config().evalRunId());
        root.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        root.putAll(extra);
        root.put("config", s.config());
        root.put("summary", Map.ofEntries(
                Map.entry("model", s.model()), Map.entry("cases", s.cases()), Map.entry("repeats", s.repeats()),
                Map.entry("metrics", s.metrics()), Map.entry("llmCalls", s.llmCalls()),
                Map.entry("repairCalls", s.repairCalls()), Map.entry("llmErrors", s.llmErrors()),
                Map.entry("callsByModel", s.callsByModel()), Map.entry("failureReasons", s.failureReasons()),
                Map.entry("recallByDifficulty", s.recallByDifficulty()),
                Map.entry("recallByRoutedModel", s.recallByModelGroup()),
                Map.entry("llmP50Ms", String.valueOf(s.llmP50())), Map.entry("llmP95Ms", String.valueOf(s.llmP95())),
                Map.entry("e2eP95Ms", String.valueOf(s.e2eP95())),
                Map.entry("avgInputTokens", String.valueOf(s.avgInputTokens())),
                Map.entry("avgOutputTokens", String.valueOf(s.avgOutputTokens())),
                Map.entry("avgCacheReadTokens", String.valueOf(s.avgCacheReadTokens())),
                Map.entry("avgCacheCreationTokens", String.valueOf(s.avgCacheCreationTokens())),
                Map.entry("costPerReqUsd", String.valueOf(s.costPerReq())),
                Map.entry("cacheHitRate", String.valueOf(s.cacheHitRate()))));
        root.put("runs", s.details());
        Path out = dir.resolve(s.config().evalRunId() + ".json");
        Files.writeString(out, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        return out;
    }
}
