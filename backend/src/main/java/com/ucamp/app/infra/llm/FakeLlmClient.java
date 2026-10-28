package com.ucamp.app.infra.llm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 테스트·로컬용 가짜 LLM. 실제 API를 부르지 않는다.
 * - 응답은 responder로 바꿀 수 있다. 기본은 비타민 D 하나를 추천하는 고정 답
 * - 요청에 출력 스키마가 있으면 구조화 출력(output)으로, 없으면 V0 형식 JSON 텍스트로 답한다
 * - 토큰 사용량은 실제 값이 아니라 글자 수로 계산한 고정 추정치(3글자 ≈ 1토큰)다. 비용 비교에 쓰지 않는다
 * - promptCache 요청은 API의 프롬프트 캐싱 규칙을 흉내 낸다: 고정 부분(스키마 + 시스템)이 최소 길이(추정 1024토큰) 이상이면
 *   모델별 첫 요청은 캐시 쓰기(cache_creation), 같은 고정 부분의 다음 요청은 캐시 읽기(cache_read)로 센다 (만료 없음)
 */
public class FakeLlmClient implements LlmClient {
    private static final String FAKE_MODEL_SUFFIX = "-fake";
    /** GPT-5.6 이후 모델의 최소 캐시 길이 (OpenAI 프롬프트 캐싱 문서) */
    static final int MIN_CACHEABLE_TOKENS = 1024;
    /** 토큰 추정: 3글자 ≈ 1토큰, 출력에는 고정 50토큰을 더한다 */
    private static final int CHARS_PER_TOKEN = 3;
    private static final int OUTPUT_TOKEN_OVERHEAD = 50;

    private final ObjectMapper objectMapper;
    private final Set<String> cachedPrefixes = ConcurrentHashMap.newKeySet();
    private final AtomicReference<Function<LlmRequest, LlmResponse>> responder = new AtomicReference<>();
    private final List<LlmRequest> requests = Collections.synchronizedList(new ArrayList<>());

    public FakeLlmClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        reset();
    }

    @Override
    public LlmResponse call(LlmRequest request) {
        requests.add(request);
        return responder.get().apply(request);
    }

    public void setResponder(Function<LlmRequest, LlmResponse> responder) {
        this.responder.set(responder);
    }

    // 요청 형식에 맞춰 같은 답을 돌려준다
    public void answerWith(FakeAnswer answer) {
        setResponder(req -> render(req, answer));
    }

    public void reset() {
        requests.clear();
        cachedPrefixes.clear();
        answerWith(new FakeAnswer(List.of(new FakeAnswer.Item("VITAMIN_D", "비타민 D",
                "가짜 응답: 햇빛 노출이 적은 경우를 가정", BigDecimal.TEN, "mcg")), "가짜 응답입니다"));
    }

    public List<LlmRequest> requests() {
        return List.copyOf(requests);
    }

    // ---------- 응답 만들기 ----------

    public LlmResponse render(LlmRequest request, FakeAnswer answer) {
        if (request.outputSchema() != null && QUESTION_SCHEMA.equals(request.outputSchema().name())) {
            return structuredResponse(request, questionOutput(request));
        }
        if (request.outputSchema() != null && QUESTIONS_SCHEMA.equals(request.outputSchema().name())) {
            return structuredResponse(request, questionsOutput(request));
        }
        if (request.outputSchema() != null) {
            return structuredResponse(request, structuredOutput(request.outputSchema(), answer));
        }
        return textResponse(request, v0Text(answer));
    }

    public LlmResponse textResponse(LlmRequest request, String text) {
        return base(request, text).stopReason("stop").build();
    }

    public LlmResponse structuredResponse(LlmRequest request, JsonNode output) {
        return base(request, "").output(output).stopReason("stop").build();
    }

    private LlmResponse.LlmResponseBuilder base(LlmRequest request, String outputText) {
        String prefix = (request.system() == null ? "" : request.system())
                + (request.outputSchema() == null ? "" : String.valueOf(request.outputSchema().schema()));
        int prefixTokens = prefix.length() / CHARS_PER_TOKEN;
        int messageTokens = request.messages().stream().mapToInt(m -> m.text().length()).sum() / CHARS_PER_TOKEN;
        int cacheRead = 0;
        int cacheCreation = 0;
        int input = prefixTokens + messageTokens;
        if (request.promptCache() && prefixTokens >= MIN_CACHEABLE_TOKENS) {
            String key = request.model() + "|" + prefix.hashCode();
            if (cachedPrefixes.add(key)) {
                cacheCreation = prefixTokens;
            } else {
                cacheRead = prefixTokens;
            }
            input = messageTokens;
        }
        return LlmResponse.builder()
                .text(outputText)
                .usage(new LlmUsage(Math.max(1, input), Math.max(1, outputText.length() / CHARS_PER_TOKEN + OUTPUT_TOKEN_OVERHEAD), cacheRead, cacheCreation))
                .latencyMs(0)
                .model((request.model() == null ? "unknown" : request.model()) + FAKE_MODEL_SUFFIX)
                .requestId("fake-" + System.nanoTime());
    }

    private String v0Text(FakeAnswer answer) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode recs = root.putArray("recommendations");
        for (FakeAnswer.Item item : answer.items()) {
            ObjectNode n = recs.addObject();
            n.put("name", item.displayName() != null ? item.displayName() : item.code());
            n.put("reason", item.reason());
            if (item.amount() != null) {
                n.put("amount", item.amount());
                n.put("unit", item.unit());
            }
        }
        root.put("summary", answer.summary());
        return root.toString();
    }

    @SuppressWarnings("unchecked")
    private JsonNode structuredOutput(LlmOutputSchema outputSchema, FakeAnswer answer) {
        // 스키마에 amount가 있을 때만(V1) 용량을 넣는다
        boolean hasAmount = false;
        Object recs = ((Map<String, Object>) outputSchema.schema().getOrDefault("properties", Map.of())).get("recommendations");
        if (recs instanceof Map<?, ?> recsMap && recsMap.get("items") instanceof Map<?, ?> items
                && items.get("properties") instanceof Map<?, ?> props) {
            hasAmount = props.containsKey("amount");
        }
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode arr = root.putArray("recommendations");
        int priority = 1;
        for (FakeAnswer.Item item : answer.items()) {
            ObjectNode n = arr.addObject();
            n.put("ingredient", item.code());
            n.put("reason", item.reason());
            n.put("priority", priority++);
            if (hasAmount) {
                if (item.amount() != null) {
                    n.put("amount", item.amount());
                    n.put("unit", item.unit());
                } else {
                    n.putNull("amount");
                    n.putNull("unit");
                }
            }
        }
        root.put("summary", answer.summary());
        return root;
    }

    // ---------- V2 맞춤 질문 (submit_question 스키마) ----------

    /** PersonalizedQuestionSchema.NAME (infra가 domain을 참조하지 않도록 이름만 맞춘다) */
    public static final String QUESTION_SCHEMA = "submit_question";
    private static final Pattern TOPIC_LINE = Pattern.compile("- topic: (\\S+)");
    private static final Pattern DEPTH_LINE = Pattern.compile("- depth: (\\d+)");

    /**
     * 질문 스키마에 맞는 고정 응답. 사용자 메시지의 topic·depth·answerType을 읽어 매번 다른 질문 문장을 만든다.
     * MULTI(불편한 점)는 CONCERN 선택지 2개, SINGLE은 CONCERN / NORMAL / NONE / UNKNOWN 하나씩.
     */
    public JsonNode questionOutput(LlmRequest request) {
        String user = request.messages().isEmpty() ? "" : request.messages().get(request.messages().size() - 1).text();
        boolean multi = user.contains("- answerType: MULTI");
        String topic = find(TOPIC_LINE, user, "TOPIC");
        String depth = find(DEPTH_LINE, user, "1");
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode options = root.putArray("options");
        if (multi) {
            root.put("question", "가짜 질문: 요즘 불편한 점을 골라주세요 (최대 2개)");
            root.put("answerType", "MULTI");
            option(options, "쉽게 피곤해요", "CONCERN");
            option(options, "잠을 잘 못 자요", "CONCERN");
        } else {
            String shortTopic = topic.length() <= 30 ? topic : topic.substring(0, 30);
            root.put("question", "가짜 질문: " + shortTopic + " " + depth + "단계 확인");
            root.put("answerType", "SINGLE");
            option(options, "그런 편이에요", "CONCERN");
            option(options, "괜찮아요", "NORMAL");
        }
        option(options, "없어요", "NONE");
        option(options, "잘 모르겠어요", "UNKNOWN");
        return root;
    }

    /** QuestionBatchSchema.NAME (질문 미리 만들기 묶음) */
    public static final String QUESTIONS_SCHEMA = "submit_questions";
    private static final Pattern BATCH_LINE = Pattern.compile("- id (\\d+): topic (.+?) \\(.*answerType: (SINGLE|MULTI)(.*)");

    /**
     * 묶음 스키마에 맞는 고정 응답. 사용자 메시지의 "- id N: topic ..." 줄마다 단건과 같은 모양의 질문을 만든다.
     * "followup: 필요"인 주제는 2단계 질문도 넣는다. 질문 문장에 topic 코드를 넣어 서로 겹치지 않게 한다
     */
    public JsonNode questionsOutput(LlmRequest request) {
        String user = request.messages().isEmpty() ? "" : request.messages().get(request.messages().size() - 1).text();
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode questions = root.putArray("questions");
        for (String line : user.split("\n")) {
            Matcher m = BATCH_LINE.matcher(line);
            if (!m.matches()) {
                continue;
            }
            ObjectNode q = questions.addObject();
            q.put("id", Integer.parseInt(m.group(1)));
            fakeQuestion(q, m.group(2), "1", "MULTI".equals(m.group(3)));
            if (m.group(4).contains("followup: 필요")) {
                fakeQuestion(q.putObject("followup"), m.group(2), "2", false);
            } else {
                q.putNull("followup");
            }
        }
        return root;
    }

    private static void fakeQuestion(ObjectNode root, String topic, String depth, boolean multi) {
        ArrayNode options = root.putArray("options");
        if (multi) {
            root.put("question", "가짜 질문: 요즘 불편한 점을 골라주세요 (최대 2개)");
            root.put("answerType", "MULTI");
            option(options, "쉽게 피곤해요", "CONCERN");
            option(options, "잠을 잘 못 자요", "CONCERN");
        } else {
            String shortTopic = topic.length() <= 30 ? topic : topic.substring(0, 30);
            root.put("question", "가짜 질문: " + shortTopic + " " + depth + "단계 확인");
            root.put("answerType", "SINGLE");
            option(options, "그런 편이에요", "CONCERN");
            option(options, "괜찮아요", "NORMAL");
        }
        option(options, "없어요", "NONE");
        option(options, "잘 모르겠어요", "UNKNOWN");
    }

    private static void option(ArrayNode options, String label, String kind) {
        ObjectNode o = options.addObject();
        o.put("label", label);
        o.put("kind", kind);
    }

    private static String find(Pattern pattern, String text, String fallback) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : fallback;
    }

    /** 가짜 답 내용. code는 성분 코드, displayName은 V0 텍스트 응답에 쓸 이름 */
    public record FakeAnswer(List<Item> items, String summary) {
        public record Item(String code, String displayName, String reason, BigDecimal amount, String unit) {
        }
    }
}
