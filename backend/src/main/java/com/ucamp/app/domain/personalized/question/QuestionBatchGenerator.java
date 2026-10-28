package com.ucamp.app.domain.personalized.question;

import com.ucamp.app.domain.personalized.planner.Topic;
import com.ucamp.app.domain.personalized.question.PersonalizedQuestionGenerator.PastQuestion;
import com.ucamp.app.domain.personalized.question.PersonalizedQuestionValidator.Violation;
import com.ucamp.app.domain.recommendation.pipeline.InputFormatter;
import com.ucamp.app.domain.recommendation.pipeline.PromptTemplates;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.infra.llm.LlmClient;
import com.ucamp.app.infra.llm.LlmMessage;
import com.ucamp.app.infra.llm.LlmProperties;
import com.ucamp.app.infra.llm.LlmRequest;
import com.ucamp.app.infra.llm.LlmResponse;
import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 맞춤 질문 여러 개를 한 번의 호출로 생성 (질문 미리 만들기). 출력 스키마는 질문 배열(submit_questions).
 * - 질문마다 단건과 같은 검증기를 적용하고 통과한 것만 돌려준다. 묶음은 재요청하지 않는다 (실패한 주제는 그때 단건 생성)
 * - followup = 1단계 질문에서 CONCERN을 골랐다고 가정한 2단계 질문 (실제 고른 선택지는 모르고 만든다)
 * - 하나도 통과하지 못하면 로그 행 outcome = VALIDATION_FAIL
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionBatchGenerator {
    /** 질문 하나당 출력 토큰 (단건 500보다 조금 넉넉히, followup이 있으면 두 배) */
    static final int TOKENS_PER_QUESTION = 600;

    private final LlmClient llmClient;
    private final LlmCallLogService llmCallLogService;
    private final LlmProperties llmProperties;
    private final PromptTemplates promptTemplates;
    private final InputFormatter inputFormatter;
    private final PersonalizedQuestionValidator validator;

    /** 이번 묶음에서 만들 주제. followup = 이 주제의 2단계 질문도 미리 만든다 */
    public record Item(Topic topic, boolean followup) {
    }

    public record Request(Long sessionId, RecommendationInput known, List<Item> items, List<PastQuestion> history,
                          String callTag) {
    }

    /**
     * @param questions 통과한 질문. 키 = Prefetch 키 (topic코드#depth)
     * @param dropped   검증에 걸려 버린 질문 수 (followup 포함)
     */
    public record Result(Map<String, GeneratedQuestion> questions, int dropped, Long callLogId) {
    }

    public static String key(Topic topic, int depth) {
        return topic.code() + "#" + depth;
    }

    /** LLM 호출 실패는 그대로 던진다 (LlmCallException, LlmUnavailableException). 호출하는 쪽이 단건 생성으로 대신한다 */
    public Result generate(Request req) {
        boolean anyFollowup = req.items().stream().anyMatch(Item::followup);
        LlmRequest request = LlmRequest.builder()
                .model(llmProperties.getModel())
                .system(promptTemplates.load("pq/system.txt"))
                .messages(List.of(LlmMessage.user(userMessage(req))))
                .maxTokens(req.items().size() * TOKENS_PER_QUESTION * (anyFollowup ? 2 : 1))
                .reasoningEffort(PersonalizedQuestionGenerator.REASONING_EFFORT)
                .outputSchema(QuestionBatchSchema.schema(anyFollowup))
                .callTag(req.callTag())
                .pipelineVersion(PersonalizedQuestionGenerator.PIPELINE_VERSION)
                .promptVersion(PersonalizedQuestionGenerator.PROMPT_VERSION)
                .sessionId(req.sessionId())
                .build();
        LlmResponse response = llmClient.call(request);
        Result result = parse(req, response);
        if (result.questions().isEmpty()) {
            llmCallLogService.markOutcome(response.callLogId(), LlmCallLog.VALIDATION_FAIL);
        }
        return result;
    }

    private Result parse(Request req, LlmResponse response) {
        Map<String, GeneratedQuestion> accepted = new LinkedHashMap<>();
        int dropped = 0;
        JsonNode list = response.output() == null ? null : response.output().get("questions");
        List<String> previous = new ArrayList<>(req.history().stream().map(PastQuestion::question).toList());
        for (int i = 0; i < req.items().size(); i++) {
            Item item = req.items().get(i);
            JsonNode node = find(list, i + 1);
            GeneratedQuestion first = node == null ? null : accept(item.topic(), item.topic().answerType(), node,
                    response, previous, req.callTag());
            if (first == null) {
                dropped += item.followup() ? 2 : 1;
                continue;
            }
            accepted.put(key(item.topic(), 1), first);
            previous.add(first.question());
            if (item.followup()) {
                JsonNode follow = node.get("followup");
                GeneratedQuestion second = follow == null || follow.isNull() ? null
                        : accept(item.topic(), item.topic().answerType(), follow, response, previous, req.callTag());
                if (second == null) {
                    dropped++;
                } else {
                    accepted.put(key(item.topic(), 2), second);
                    previous.add(second.question());
                }
            }
        }
        if (dropped > 0) {
            log.info("personalized question batch dropped {} question(s): session={}, tag={}", dropped,
                    req.sessionId(), req.callTag());
        }
        return new Result(accepted, dropped, response.callLogId());
    }

    private GeneratedQuestion accept(Topic topic, String answerType, JsonNode node, LlmResponse response,
                                     List<String> previous, String tag) {
        List<Violation> errors = validator.validate(node, answerType, previous);
        if (!errors.isEmpty()) {
            log.debug("batch question rejected: topic={}, errors={}", topic.code(),
                    errors.stream().map(Violation::code).toList());
            return null;
        }
        return PersonalizedQuestionGenerator.accepted(node, answerType, response.callLogId(),
                List.of(PersonalizedQuestionGenerator.attempt(tag, response, node, errors)));
    }

    private static JsonNode find(JsonNode list, int id) {
        if (list == null || !list.isArray()) {
            return null;
        }
        for (JsonNode n : list) {
            if (n.path("id").asInt(-1) == id) {
                return n;
            }
        }
        return null;
    }

    String userMessage(Request req) {
        boolean anyFollowup = req.items().stream().anyMatch(Item::followup);
        String topics = IntStream.range(0, req.items().size())
                .mapToObj(i -> topicLine(i + 1, req.items().get(i)))
                .collect(Collectors.joining("\n"));
        String followupRule = anyFollowup
                ? "- followup이 \"필요\"인 주제는, 사용자가 그 질문에서 CONCERN 선택지를 골랐다고 가정하고 이어서 물을 2단계 질문을"
                + " followup에 넣는다 (목적은 그 주제의 2단계 목적). 필요 없는 주제는 followup을 null로 둔다"
                : "";
        return promptTemplates.render("pq/batch-user.txt", Map.of(
                "known", inputFormatter.formatKnown(req.known()),
                "topics", topics,
                "history", PersonalizedQuestionGenerator.history(req.history()),
                "followupRule", followupRule)).strip();
    }

    private static String topicLine(int id, Item item) {
        Topic t = item.topic();
        String line = "- id " + id + ": topic " + t.code() + " (" + TopicPurposes.label(t) + ") / depth 1 / 최대 "
                + t.maxDepth() + " / answerType: " + t.answerType() + " / 목적: " + TopicPurposes.purpose(t, 1);
        if (item.followup()) {
            line += " / followup: 필요 (2단계 목적: " + TopicPurposes.purpose(t, 2) + ")";
        }
        return line;
    }
}
