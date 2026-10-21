package com.ucamp.app.domain.recommendation.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.ucamp.app.domain.health.entity.IngredientCode;
import com.ucamp.app.domain.recommendation.cache.CachedLlmOutput;
import com.ucamp.app.infra.llm.LlmMessage;
import com.ucamp.app.infra.llm.LlmRequest;
import com.ucamp.app.infra.llm.LlmResponse;
import com.ucamp.app.infra.llm.log.LlmCallLog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * V1부터의 공통 흐름: tool use로 출력 형식 강제 → 검증 → 실패 시 이유를 붙여 1회 재요청 → 후처리.
 * 버전별 차이는 Options와 아래 훅(postProcess 등)으로만 둔다. 각 버전 클래스는 지우지 않고 공존한다.
 * - 첫 호출 검증 실패: 그 로그 행 outcome = VALIDATION_FAIL, 재요청은 call_tag = RECOMMEND_REPAIR
 * - 재요청도 실패: 재요청 로그 행 outcome = PARSE_FAIL, 결과 parseSuccess = false (빈 결과)
 */
public abstract class StructuredPipeline implements RecommendationPipeline {

    /**
     * @param llmDose true면 LLM이 amount·unit을 채운다 (V1). false면 스키마에서 빼고 코드로 계산 (V2~)
     */
    protected record Options(String version, String promptDir, String promptVersion, boolean llmDose) {
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

    protected Options options() {
        return options;
    }

    /** LLM 단계 결과: 후처리 전 항목, 요약, 모델, 호출 로그 ID */
    private record LlmPart(boolean parseSuccess, List<RecommendationItem> items, String summary, String model,
                           List<Long> logIds, String failureReason) {
    }

    @Override
    public RecommendationResult run(RecommendationInput input) {
        String version = options.version();
        String model = chooseModel(input);
        boolean cacheEnabled = resultCache();
        String cacheKey = cacheEnabled
                ? deps.getCacheKeyFactory().key(input, version, options.promptVersion(), model) : null;

        LlmPart part = null;
        boolean cacheHit = false;
        if (cacheEnabled) {
            Optional<CachedLlmOutput> cached = deps.getStageTimer().record("cache", version,
                    () -> deps.getResultCacheService().get(cacheKey));
            if (cached.isPresent()) {
                part = fromCache(cached.get());
                cacheHit = true;
            }
        }
        if (part == null) {
            part = callLlm(input, model);
            if (cacheEnabled && part.parseSuccess()) {
                deps.getResultCacheService().put(cacheKey, toCache(part));
            }
        }
        RecommendationResult.RecommendationResultBuilder result = RecommendationResult.builder()
                .pipelineVersion(version).promptVersion(options.promptVersion()).model(part.model())
                .llmCallLogIds(part.logIds()).cacheHit(cacheHit);
        if (!part.parseSuccess()) {
            return result.parseSuccess(false).items(List.of()).failureReason(part.failureReason()).build();
        }
        List<RecommendationItem> processed = postProcess(input, part.items());
        return result.parseSuccess(true).items(processed).summary(part.summary()).build();
    }

    private LlmPart callLlm(RecommendationInput input, String model) {
        String version = options.version();
        String system = deps.getPromptTemplates().render(options.promptDir() + "/system.txt",
                Map.of("ingredients", ingredientList()));
        String user = deps.getPromptTemplates().render(options.promptDir() + "/user.txt",
                Map.of("input", deps.getInputFormatter().format(input)));
        String extra = extraUserText(input);
        if (extra != null && !extra.isBlank()) {
            user = user + "\n\n" + extra;
        }

        List<Long> logIds = new ArrayList<>();
        LlmResponse first = call(input, system, user, model, LlmRequest.TAG_RECOMMEND);
        logIds.add(first.callLogId());
        List<String> errors = deps.getStageTimer().record("parse", version,
                () -> deps.getValidator().validate(first.toolInput(), options.llmDose()));
        LlmResponse used = first;
        if (!errors.isEmpty()) {
            deps.getLlmCallLogService().markOutcome(first.callLogId(), LlmCallLog.VALIDATION_FAIL);
            String repairUser = user + "\n\n" + deps.getPromptTemplates().render("common/repair.txt",
                    Map.of("errors", errors.stream().map(e -> "- " + e).collect(Collectors.joining("\n"))));
            LlmResponse repair = call(input, system, repairUser, model, LlmRequest.TAG_RECOMMEND_REPAIR);
            logIds.add(repair.callLogId());
            List<String> repairErrors = deps.getStageTimer().record("parse", version,
                    () -> deps.getValidator().validate(repair.toolInput(), options.llmDose()));
            if (!repairErrors.isEmpty()) {
                deps.getLlmCallLogService().markOutcome(repair.callLogId(), LlmCallLog.PARSE_FAIL);
                return new LlmPart(false, List.of(), null, repair.model(), logIds, "VALIDATION_FAIL_AFTER_REPAIR");
            }
            used = repair;
        }
        return new LlmPart(true, toItems(used.toolInput()), used.toolInput().path("summary").asText(null),
                used.model(), logIds, null);
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

    private LlmResponse call(RecommendationInput input, String system, String user, String model, String tag) {
        LlmRequest request = LlmRequest.builder()
                .model(model)
                .system(system)
                .messages(List.of(LlmMessage.user(user)))
                .maxTokens(deps.getLlmProperties().getMaxTokens())
                .tools(List.of(RecommendationToolSchema.tool(options.llmDose())))
                .toolName(RecommendationToolSchema.TOOL_NAME)
                .cacheControl(promptCache())
                .callTag(tag)
                .pipelineVersion(options.version())
                .promptVersion(options.promptVersion())
                .responseId(input.responseId())
                .evalRunId(input.evalRunId())
                .build();
        return deps.getStageTimer().record("llm", options.version(), () -> deps.getLlmClient().call(request));
    }

    private List<RecommendationItem> toItems(JsonNode toolInput) {
        List<RecommendationItem> items = new ArrayList<>();
        for (JsonNode r : toolInput.get("recommendations")) {
            BigDecimal amount = null;
            String unit = null;
            if (options.llmDose() && r.hasNonNull("amount")) {
                amount = r.get("amount").decimalValue();
                unit = r.path("unit").asText(null);
            }
            items.add(RecommendationItem.builder()
                    .ingredient(r.get("ingredient").asText())
                    .reason(r.get("reason").asText())
                    .sortOrder(r.get("priority").asInt())
                    .amount(amount)
                    .unit(unit)
                    .build());
        }
        items.sort(Comparator.comparingInt(RecommendationItem::sortOrder));
        return items;
    }

    private static String ingredientList() {
        return Arrays.stream(IngredientCode.values())
                .map(c -> "- " + c.name() + ": " + c.getNameKo() + ", " + c.getUnit())
                .collect(Collectors.joining("\n"));
    }

    // ---------- 버전별 훅 ----------

    /** 사용자 메시지 뒤에 붙일 내용 (V4: 금지 성분 목록). 캐시 지점 뒤에 와야 해서 시스템이 아니라 여기에 둔다 */
    protected String extraUserText(RecommendationInput input) {
        return null;
    }

    /** LLM 출력 후처리 (V2: 용량 계산, V3: 사후 규칙) */
    protected List<RecommendationItem> postProcess(RecommendationInput input, List<RecommendationItem> items) {
        return items;
    }

    /** 사용할 모델 (V8: 라우팅) */
    protected String chooseModel(RecommendationInput input) {
        return deps.getLlmProperties().getModel();
    }

    /** LLM 출력 결과 캐시 사용 여부 (V6) */
    protected boolean resultCache() {
        return false;
    }

    /** 프롬프트 캐싱 지점 표시 여부 (V7) */
    protected boolean promptCache() {
        return false;
    }
}
