package com.ucamp.app.domain.recommendation.pipeline;

import tools.jackson.databind.JsonNode;
import com.ucamp.app.domain.health.entity.IngredientCode;
import com.ucamp.app.domain.recommendation.cache.CachedLlmOutput;
import com.ucamp.app.domain.recommendation.rule.RuleEngine;
import com.ucamp.app.global.metrics.StageTimer;
import com.ucamp.app.infra.llm.LlmRequest;
import com.ucamp.app.infra.llm.LlmResponse;
import com.ucamp.app.infra.llm.log.LlmCallLog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * V1부터의 공통 흐름: 구조화 출력(JSON 스키마 강제) → 검증 → 실패 시 이유를 붙여 1회 재요청 → 후처리.
 * 버전별 차이는 Options(프롬프트 + 기능 목록)로만 둔다. 각 버전 클래스는 Options 하나만 정한다.
 * - 첫 호출 검증 실패: 그 로그 행 outcome = VALIDATION_FAIL, 재요청은 call_tag = RECOMMEND_REPAIR
 * - 재요청도 실패: 재요청 로그 행 outcome = PARSE_FAIL, 결과 parseSuccess = false (빈 결과)
 */
public abstract class StructuredPipeline implements RecommendationPipeline {

    /** 프롬프트 폴더(src/main/resources/prompts/{dir})와 기록용 프롬프트 버전 */
    protected record Prompt(String dir, String version) {
        static final Prompt V1 = new Prompt("v1", "v1.1");
        static final Prompt V2 = new Prompt("v2", "v2.1");
        static final Prompt V4 = new Prompt("v4", "v4.1");
    }

    /** 버전마다 켜는 기능 */
    protected enum Feature {
        /** 용량을 기준표로 계산 (V2~). 꺼져 있으면 LLM이 amount·unit을 채운다 (V1) */
        DOSE_CALC,
        /** 사후 규칙 검증 (V3~) */
        RULE_POST,
        /** 금지 성분 목록을 사용자 메시지 뒤에 넣음 (V4~) */
        BANNED_LIST,
        /** LLM 출력 결과 캐시 (V6) */
        RESULT_CACHE,
        /** prompt_cache_key로 고정 부분 캐싱 (V7~) */
        PROMPT_CACHE,
        /** 입력 복잡도로 모델 선택 (V8) */
        MODEL_ROUTING
    }

    protected record Options(String version, Prompt prompt, Set<Feature> features) {
        protected Options(String version, Prompt prompt, Feature... features) {
            this(version, prompt, features.length == 0 ? EnumSet.noneOf(Feature.class) : EnumSet.copyOf(List.of(features)));
        }

        boolean has(Feature feature) {
            return features.contains(feature);
        }

        boolean llmDose() {
            return !has(Feature.DOSE_CALC);
        }
    }

    protected final PipelineDeps deps;
    private final Options options;

    protected StructuredPipeline(PipelineDeps deps, Options options) {
        this.deps = deps;
        this.options = options;
    }

    @Override
    public String version() {
        return options.version();
    }

    /** LLM 단계 결과: 후처리 전 항목, 요약, 모델, 호출 로그 ID */
    private record LlmPart(boolean parseSuccess, List<RecommendationItem> items, String summary, String model,
                           List<Long> logIds, String failureReason) {
    }

    @Override
    public RecommendationResult run(RecommendationInput input) {
        String model = chooseModel(input);
        String cacheKey = options.has(Feature.RESULT_CACHE)
                ? deps.getCacheKeyFactory().key(input, version(), options.prompt().version(), model) : null;

        Optional<LlmPart> cached = cacheKey == null ? Optional.empty() : fromCache(cacheKey);
        LlmPart part = cached.orElseGet(() -> callLlm(input, model));
        if (cacheKey != null && cached.isEmpty() && part.parseSuccess()) {
            deps.getResultCacheService().put(cacheKey, toCache(part));
        }
        if (!part.parseSuccess()) {
            return RecommendationResult.parseFailure(version(), options.prompt().version(), part.model(),
                    part.logIds(), part.failureReason());
        }
        return RecommendationResult.builder()
                .pipelineVersion(version()).promptVersion(options.prompt().version()).model(part.model())
                .llmCallLogIds(part.logIds()).cacheHit(cached.isPresent())
                .parseSuccess(true).items(postProcess(input, part.items())).summary(part.summary())
                .build();
    }

    private String chooseModel(RecommendationInput input) {
        return options.has(Feature.MODEL_ROUTING)
                ? deps.getModelRouter().route(input) : deps.getLlmProperties().getModel();
    }

    private List<RecommendationItem> postProcess(RecommendationInput input, List<RecommendationItem> items) {
        List<RecommendationItem> result = items;
        if (options.has(Feature.DOSE_CALC)) {
            result = deps.getPostProcessSteps().applyDoses(version(), input, result);
        }
        if (options.has(Feature.RULE_POST)) {
            result = deps.getPostProcessSteps().applyRules(version(), input, result);
        }
        return result;
    }

    // ---------- LLM 호출 ----------

    private LlmPart callLlm(RecommendationInput input, String model) {
        String system = deps.getPromptTemplates().render(options.prompt().dir() + "/system.txt",
                Map.of("ingredients", ingredientList()));
        String user = userMessage(input);

        List<Long> logIds = new ArrayList<>();
        LlmResponse first = call(input, system, user, model, LlmRequest.TAG_RECOMMEND);
        logIds.add(first.callLogId());
        List<String> errors = validate(first);
        if (errors.isEmpty()) {
            return parsed(first, logIds);
        }
        deps.getLlmCallLogService().markOutcome(first.callLogId(), LlmCallLog.VALIDATION_FAIL);
        String repairUser = user + "\n\n" + deps.getPromptTemplates().render("common/repair.txt",
                Map.of("errors", PromptTemplates.bulletList(errors)));
        LlmResponse repair = call(input, system, repairUser, model, LlmRequest.TAG_RECOMMEND_REPAIR);
        logIds.add(repair.callLogId());
        if (!validate(repair).isEmpty()) {
            deps.getLlmCallLogService().markOutcome(repair.callLogId(), LlmCallLog.PARSE_FAIL);
            return new LlmPart(false, List.of(), null, repair.model(), logIds, "VALIDATION_FAIL_AFTER_REPAIR");
        }
        return parsed(repair, logIds);
    }

    /** 사용자 입력. 금지 목록은 사용자마다 달라서 고정 부분(시스템 프롬프트) 뒤인 여기에 붙인다 (프롬프트 캐싱) */
    private String userMessage(RecommendationInput input) {
        String user = deps.getPromptTemplates().render(options.prompt().dir() + "/user.txt",
                Map.of("input", deps.getInputFormatter().format(input)));
        if (!options.has(Feature.BANNED_LIST)) {
            return user;
        }
        List<RuleEngine.Banned> banned = deps.getStageTimer().record(StageTimer.RULE_PRE, version(),
                () -> deps.getRuleService().preFilter(input));
        if (banned.isEmpty()) {
            return user;
        }
        String list = PromptTemplates.bulletList(banned.stream()
                .map(b -> b.ingredient() + ": " + b.rule().messageKo()).toList());
        return user + "\n\n" + deps.getPromptTemplates().render("v4/banned.txt", Map.of("banned", list));
    }

    private LlmResponse call(RecommendationInput input, String system, String user, String model, String tag) {
        LlmRequest request = PipelineRequests.recommend(input, version(), options.prompt().version(), model,
                        system, user, deps.getLlmProperties().getMaxTokens(), tag)
                .tools(List.of(RecommendationToolSchema.tool(options.llmDose())))
                .toolName(RecommendationToolSchema.TOOL_NAME)
                .cacheControl(options.has(Feature.PROMPT_CACHE))
                .build();
        return deps.getStageTimer().record(StageTimer.LLM, version(), () -> deps.getLlmClient().call(request));
    }

    private List<String> validate(LlmResponse response) {
        return deps.getStageTimer().record(StageTimer.PARSE, version(),
                () -> deps.getValidator().validate(response.toolInput(), options.llmDose()));
    }

    private LlmPart parsed(LlmResponse response, List<Long> logIds) {
        JsonNode output = response.toolInput();
        return new LlmPart(true, toItems(output), output.path("summary").asString(null), response.model(), logIds, null);
    }

    private List<RecommendationItem> toItems(JsonNode output) {
        List<RecommendationItem> items = new ArrayList<>();
        for (JsonNode r : output.get("recommendations")) {
            BigDecimal amount = null;
            String unit = null;
            if (options.llmDose() && r.hasNonNull("amount")) {
                amount = r.get("amount").decimalValue();
                unit = r.path("unit").asString(null);
            }
            items.add(RecommendationItem.builder()
                    .ingredient(r.get("ingredient").asString())
                    .reason(r.get("reason").asString())
                    .sortOrder(r.get("priority").asInt())
                    .amount(amount)
                    .unit(unit)
                    .build());
        }
        items.sort(Comparator.comparingInt(RecommendationItem::sortOrder));
        return items;
    }

    private static String ingredientList() {
        return PromptTemplates.bulletList(Arrays.stream(IngredientCode.values())
                .map(c -> c.name() + ": " + c.getNameKo() + ", " + c.getUnit()).toList());
    }

    // ---------- 결과 캐시 (V6) ----------

    private Optional<LlmPart> fromCache(String cacheKey) {
        Optional<CachedLlmOutput> cached = deps.getStageTimer().record(StageTimer.CACHE, version(),
                () -> deps.getResultCacheService().get(cacheKey));
        return cached.map(StructuredPipeline::fromCache);
    }

    private static LlmPart fromCache(CachedLlmOutput cached) {
        List<RecommendationItem> items = cached.items().stream()
                .map(i -> RecommendationItem.builder().ingredient(i.ingredient()).reason(i.reason())
                        .sortOrder(i.priority()).build())
                .sorted(Comparator.comparingInt(RecommendationItem::sortOrder))
                .toList();
        return new LlmPart(true, items, cached.summary(), cached.model(), List.of(), null);
    }

    private static CachedLlmOutput toCache(LlmPart part) {
        return new CachedLlmOutput(part.items().stream()
                .map(i -> new CachedLlmOutput.Item(i.ingredient(), i.reason(), i.sortOrder())).toList(),
                part.summary(), part.model());
    }
}
