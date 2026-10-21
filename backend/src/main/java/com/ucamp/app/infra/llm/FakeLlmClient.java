package com.ucamp.app.infra.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * 테스트·로컬용 가짜 LLM. 실제 API를 부르지 않는다.
 * - 응답은 responder로 바꿀 수 있다. 기본은 비타민 D 하나를 추천하는 고정 답
 * - tool 정의가 있으면 tool_use 입력으로, 없으면 V0 형식 JSON 텍스트로 답한다
 * - 토큰 사용량은 실제 값이 아니라 글자 수로 계산한 고정 추정치(3글자 ≈ 1토큰)다. 비용 비교에 쓰지 않는다
 */
public class FakeLlmClient implements LlmClient {
    public static final String FAKE_MODEL_SUFFIX = "-fake";

    private final ObjectMapper objectMapper;
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
        answerWith(new FakeAnswer(List.of(new FakeAnswer.Item("VITAMIN_D", "비타민 D",
                "가짜 응답: 햇빛 노출이 적은 경우를 가정", BigDecimal.TEN, "mcg")), "가짜 응답입니다"));
    }

    public List<LlmRequest> requests() {
        return List.copyOf(requests);
    }

    // ---------- 응답 만들기 ----------

    public LlmResponse render(LlmRequest request, FakeAnswer answer) {
        if (!request.toolsOrEmpty().isEmpty()) {
            LlmTool tool = request.toolsOrEmpty().get(0);
            return toolResponse(request, tool.name(), toolInput(tool, answer));
        }
        return textResponse(request, v0Text(answer));
    }

    public LlmResponse textResponse(LlmRequest request, String text) {
        return base(request, text).stopReason("end_turn").build();
    }

    public LlmResponse toolResponse(LlmRequest request, String toolName, JsonNode input) {
        return base(request, "").toolName(toolName).toolInput(input).stopReason("tool_use").build();
    }

    private LlmResponse.LlmResponseBuilder base(LlmRequest request, String outputText) {
        int inputChars = (request.system() == null ? 0 : request.system().length())
                + request.messages().stream().mapToInt(m -> m.text().length()).sum()
                + request.toolsOrEmpty().stream().mapToInt(t -> String.valueOf(t.inputSchema()).length()).sum();
        return LlmResponse.builder()
                .text(outputText)
                .usage(new LlmUsage(Math.max(1, inputChars / 3), Math.max(1, outputText.length() / 3 + 50), 0, 0))
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
    private JsonNode toolInput(LlmTool tool, FakeAnswer answer) {
        // 스키마에 amount가 있을 때만(V1) 용량을 넣는다
        boolean hasAmount = false;
        Object recs = ((Map<String, Object>) tool.inputSchema().getOrDefault("properties", Map.of())).get("recommendations");
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

    /** 가짜 답 내용. code는 성분 코드, displayName은 V0 텍스트 응답에 쓸 이름 */
    public record FakeAnswer(List<Item> items, String summary) {
        public record Item(String code, String displayName, String reason, BigDecimal amount, String unit) {
        }
    }
}
