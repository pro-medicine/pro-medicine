package com.ucamp.app.domain.chat.parse;

import com.ucamp.app.domain.recommendation.pipeline.PromptTemplates;
import com.ucamp.app.domain.recommendation.rule.ConditionCodeResolver;
import com.ucamp.app.infra.llm.LlmCallException;
import com.ucamp.app.infra.llm.LlmClient;
import com.ucamp.app.infra.llm.LlmMessage;
import com.ucamp.app.infra.llm.LlmOutputSchema;
import com.ucamp.app.infra.llm.LlmProperties;
import com.ucamp.app.infra.llm.LlmRequest;
import com.ucamp.app.infra.llm.LlmResponse;
import com.ucamp.app.infra.llm.LlmUnavailableException;
import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ① 대화 문장 → 구조화 JSON (설계 4-1). StructuredPipeline과 같은 흐름: 스키마 강제 → 검증 → 실패하면 이유를 붙여 1회 재요청
 * (CHAT_PARSE → CHAT_PARSE_REPAIR). 그래도 실패하면 ChatParseException (안전 추출 없이 진행하지 않는다).
 * 서버 응급·위기 키워드는 LLM 결과와 상관없이 더한다. 대화 원문은 애플리케이션 로그에 남기지 않는다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatParser {
    public static final String PIPELINE_VERSION = "chat-v1";
    // chat-v2 (2026-10-29): search_query에 약·약 계열·질환 이름을 넣지 않는다 (서버도 약 이름을 검사해 1회 재요청)
    public static final String PROMPT_VERSION = "chat-v2";
    public static final String TAG_CHAT_PARSE = "CHAT_PARSE";
    public static final String TAG_CHAT_PARSE_REPAIR = "CHAT_PARSE_REPAIR";
    static final int MAX_TOKENS = 800;
    static final String REASONING_EFFORT = "low";
    // 질환 코드의 한글 이름 (규칙 DISEASE 코드 = ConditionCodeResolver.DISEASES)
    private static final Map<String, String> CONDITION_LABELS = Map.of("HYPERTENSION", "고혈압", "DIABETES", "당뇨",
            "THYROID", "갑상선 질환", "KIDNEY", "신장 질환");

    private final LlmClient llmClient;
    private final LlmProperties llmProperties;
    private final PromptTemplates promptTemplates;
    private final LlmCallLogService llmCallLogService;
    private final DrugCatalog drugCatalog;

    /**
     * @param raw    LLM 출력 그대로 (ai_chat_turns.parse_output 저장용)
     * @param logIds llm_call_logs 행 id
     */
    public record Parsed(ChatParse parse, JsonNode raw, List<Long> logIds, List<DrugCatalog.Entry> drugs) {
    }

    /**
     * @param clarifiedQuestion 되묻기 답일 때 앞서 보여준 질문 (첫 입력이면 null)
     * @param answer            되묻기 답 (첫 입력이면 null)
     */
    public Parsed parse(String text, String clarifiedQuestion, String answer) {
        List<DrugCatalog.Entry> drugs = drugCatalog.all();
        Map<String, List<String>> classes = drugCatalog.classes(drugs);
        List<String> conditionCodes = ConditionCodeResolver.DISEASES.stream().sorted().toList();
        LlmOutputSchema schema = ChatParseOutputSchema.schema(classes.keySet(), conditionCodes);
        String system = promptTemplates.render("chat/system.txt", Map.of(
                "drugs", drugList(classes), "conditions", conditionList(conditionCodes)));
        String user = clarifiedQuestion == null
                ? promptTemplates.render("chat/user.txt", Map.of("text", text))
                : promptTemplates.render("chat/clarified.txt",
                        Map.of("text", text, "question", clarifiedQuestion, "answer", answer));

        List<String> banned = drugCatalog.searchQueryBannedTerms(drugs);

        List<Long> logIds = new ArrayList<>();
        LlmResponse first = call(system, user, schema, TAG_CHAT_PARSE);
        addLogId(logIds, first.callLogId());
        ChatParseValidator.Result result = ChatParseValidator.validate(first.output(), classes.keySet(), conditionCodes,
                banned);
        JsonNode raw = first.output();
        ChatParse validated = result.parse();
        if (!result.ok()) {
            llmCallLogService.markOutcome(first.callLogId(), LlmCallLog.VALIDATION_FAIL);
            String repairUser = user + "\n\n" + promptTemplates.render("chat/repair.txt",
                    Map.of("errors", PromptTemplates.bulletList(result.errors())));
            LlmResponse repair = call(system, repairUser, schema, TAG_CHAT_PARSE_REPAIR);
            addLogId(logIds, repair.callLogId());
            result = ChatParseValidator.validate(repair.output(), classes.keySet(), conditionCodes, banned);
            raw = repair.output();
            validated = result.parse();
            if (result.onlyBannedTerms()) {
                // 재요청 뒤에도 검색 문장에 약 이름이 남으면 그 단어만 지우고 진행 (약은 medications로 이미 받았다)
                log.info("chat search_query banned terms stripped: count={}", result.bannedFound().size());
                validated = withSearchQuery(validated, ChatParseValidator.stripTerms(validated.searchQuery(),
                        result.bannedFound()));
            } else if (!result.ok()) {
                llmCallLogService.markOutcome(repair.callLogId(), LlmCallLog.PARSE_FAIL);
                log.warn("chat parse failed after repair: errors={}", result.errors().size());
                throw new ChatParseException(ChatParseException.VALIDATION_FAIL_AFTER_REPAIR, logIds, null);
            }
        }
        ChatParse parse = postProcess(validated, text, answer, clarifiedQuestion != null);
        return new Parsed(parse, raw, List.copyOf(logIds), drugs);
    }

    private static ChatParse withSearchQuery(ChatParse p, String query) {
        return new ChatParse(p.redFlag(), p.offTopic(), p.needsClarification(), p.clarification(), query,
                p.medications(), p.conditions());
    }

    /** 서버 키워드 응급·위기 + 되묻기 질문 검증. 되묻기 답 이후에는 되묻기를 무시한다 (D5) */
    static ChatParse postProcess(ChatParse parse, String text, String answer, boolean clarifiedAlready) {
        ChatParse out = parse;
        Optional<String> keyword = RedFlagDetector.detect(text).or(() -> RedFlagDetector.detect(answer));
        if (keyword.isPresent()) {
            // 둘 다 걸리면 위기(CRISIS)를 우선
            String level = ChatParse.CRISIS.equals(out.redFlag()) ? ChatParse.CRISIS : keyword.get();
            out = out.withRedFlag(level);
        }
        if (clarifiedAlready) {
            return new ChatParse(out.redFlag(), out.offTopic(), false, null, out.searchQuery(), out.medications(),
                    out.conditions());
        }
        if (out.needsClarification()) {
            out = out.withClarification(ClarificationGuard.check(out.clarification()));
        }
        return out;
    }

    private LlmResponse call(String system, String user, LlmOutputSchema schema, String tag) {
        LlmRequest request = LlmRequest.builder()
                .model(llmProperties.getModel())
                .system(system)
                .messages(List.of(LlmMessage.user(user)))
                .maxTokens(MAX_TOKENS)
                .reasoningEffort(REASONING_EFFORT)
                .outputSchema(schema)
                .callTag(tag)
                .pipelineVersion(PIPELINE_VERSION)
                .promptVersion(PROMPT_VERSION)
                .build();
        try {
            return llmClient.call(request);
        } catch (LlmUnavailableException e) {
            log.warn("chat parse LLM unavailable: reason={}", e.getReason());
            throw new ChatParseException(ChatParseException.LLM_UNAVAILABLE, List.of(), e);
        } catch (LlmCallException e) {
            log.warn("chat parse LLM call failed: kind={}, status={}", e.getKind(), e.getHttpStatus());
            throw new ChatParseException(ChatParseException.LLM_CALL_FAILED,
                    e.getCallLogId() == null ? List.of() : List.of(e.getCallLogId()), e);
        }
    }

    private static void addLogId(List<Long> ids, Long id) {
        if (id != null) {
            ids.add(id);
        }
    }

    static String drugList(Map<String, List<String>> classes) {
        return classes.entrySet().stream()
                .map(e -> "- " + e.getKey() + ": " + String.join(", ", e.getValue()))
                .collect(Collectors.joining("\n"));
    }

    static String conditionList(List<String> codes) {
        return codes.stream().map(c -> "- " + c + ": " + CONDITION_LABELS.getOrDefault(c, c))
                .collect(Collectors.joining("\n"));
    }
}
