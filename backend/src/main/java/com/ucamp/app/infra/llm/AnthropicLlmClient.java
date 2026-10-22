package com.ucamp.app.infra.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * Claude Messages API(POST /v1/messages)를 HTTP로 직접 호출한다.
 * 형식은 Anthropic 공식 문서 기준: x-api-key, anthropic-version 헤더, content 블록(text / tool_use),
 * usage(input_tokens, output_tokens, cache_read_input_tokens, cache_creation_input_tokens), request-id 헤더.
 * 재시도·서킷 브레이커는 여기서 하지 않는다 (STEP 10 데코레이터).
 */
@Slf4j
public class AnthropicLlmClient implements LlmClient {
    static final String API_KEY_ENV = "ANTHROPIC_API_KEY";

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final Supplier<String> apiKeySupplier;
    private final RestClient restClient;

    /** 키를 OS 환경변수에서만 읽는다 (Spring 밖 테스트용). 앱에서는 LlmConfig가 backend/.env까지 읽는 supplier를 넘긴다 */
    public AnthropicLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, () -> System.getenv(API_KEY_ENV));
    }

    public AnthropicLlmClient(LlmProperties properties, ObjectMapper objectMapper, Supplier<String> apiKeySupplier) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.apiKeySupplier = apiKeySupplier;
        // 평문 http(WireMock 등)에서는 JDK HttpClient의 h2c 업그레이드 시도가 끊기는 경우가 있어 HTTP/1.1로 고정
        HttpClient.Version httpVersion = properties.getAnthropic().getBaseUrl().startsWith("https://")
                ? HttpClient.Version.HTTP_2 : HttpClient.Version.HTTP_1_1;
        HttpClient httpClient = HttpClient.newBuilder()
                .version(httpVersion)
                .connectTimeout(Duration.ofMillis(Math.min(properties.getTimeoutMs(), 10_000)))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        this.restClient = RestClient.builder()
                .baseUrl(properties.getAnthropic().getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public LlmResponse call(LlmRequest request) {
        String apiKey = apiKeySupplier.get();
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmCallException(LlmCallException.Kind.HTTP_ERROR, null, null, 0,
                    API_KEY_ENV + "가 설정되지 않았습니다 (backend/.env 또는 환경변수)", null);
        }
        ObjectNode body = buildBody(request);
        long start = System.nanoTime();
        try {
            return restClient.post()
                    .uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", properties.getAnthropic().getVersion())
                    .body(body)
                    .exchange((req, res) -> {
                        long latency = elapsedMs(start);
                        int status = res.getStatusCode().value();
                        String raw = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        String requestId = res.getHeaders().getFirst("request-id");
                        writeBody(request, body, raw, requestId);
                        if (status >= 400) {
                            throw new LlmCallException(LlmCallException.Kind.HTTP_ERROR, status,
                                    retryAfter(res.getHeaders().getFirst("retry-after")), latency,
                                    "Anthropic API HTTP " + status + " " + errorType(raw), null);
                        }
                        return parse(raw, latency, requestId, request);
                    });
        } catch (LlmCallException e) {
            throw e;
        } catch (ResourceAccessException e) {
            long latency = elapsedMs(start);
            if (hasCause(e, HttpTimeoutException.class)) {
                throw new LlmCallException(LlmCallException.Kind.TIMEOUT, null, null, latency, "LLM timeout", e);
            }
            throw new LlmCallException(LlmCallException.Kind.NETWORK, null, null, latency, e.getMessage(), e);
        }
    }

    ObjectNode buildBody(LlmRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", request.model() != null ? request.model() : properties.getModel());
        body.put("max_tokens", request.maxTokens() > 0 ? request.maxTokens() : properties.getMaxTokens());
        if (request.temperature() != null) {
            body.put("temperature", request.temperature());
        }
        if (properties.getAnthropic().getEffort() != null && !properties.getAnthropic().getEffort().isBlank()) {
            body.putObject("output_config").put("effort", properties.getAnthropic().getEffort());
        }

        // 렌더링 순서는 tools → system → messages. 고정 부분 끝(마지막 tool, system)에 캐시 지점을 둔다
        List<LlmTool> tools = request.toolsOrEmpty();
        if (!tools.isEmpty()) {
            ArrayNode toolArray = body.putArray("tools");
            for (int i = 0; i < tools.size(); i++) {
                LlmTool tool = tools.get(i);
                ObjectNode t = toolArray.addObject();
                t.put("name", tool.name());
                t.put("description", tool.description());
                t.set("input_schema", objectMapper.valueToTree(tool.inputSchema()));
                if (tool.strict()) {
                    t.put("strict", true);
                }
                if (request.cacheControl() && i == tools.size() - 1) {
                    t.putObject("cache_control").put("type", "ephemeral");
                }
            }
            ObjectNode choice = body.putObject("tool_choice");
            if (request.toolName() != null && properties.getAnthropic().isForceToolChoice()) {
                choice.put("type", "tool");
                choice.put("name", request.toolName());
            } else {
                choice.put("type", "auto");
            }
        }
        if (request.system() != null && !request.system().isBlank()) {
            ObjectNode systemBlock = body.putArray("system").addObject();
            systemBlock.put("type", "text");
            systemBlock.put("text", request.system());
            if (request.cacheControl()) {
                systemBlock.putObject("cache_control").put("type", "ephemeral");
            }
        }
        ArrayNode messages = body.putArray("messages");
        for (LlmMessage m : request.messages()) {
            ObjectNode msg = messages.addObject();
            msg.put("role", m.role());
            ObjectNode block = msg.putArray("content").addObject();
            block.put("type", "text");
            block.put("text", m.text());
        }
        return body;
    }

    LlmResponse parse(String raw, long latencyMs, String requestId, LlmRequest request) throws IOException {
        JsonNode root = objectMapper.readTree(raw);
        StringBuilder text = new StringBuilder();
        String toolName = null;
        JsonNode toolInput = null;
        for (JsonNode block : root.path("content")) {
            String type = block.path("type").asText();
            if ("text".equals(type)) {
                text.append(block.path("text").asText());
            } else if ("tool_use".equals(type) && toolInput == null
                    && (request.toolName() == null || request.toolName().equals(block.path("name").asText()))) {
                toolName = block.path("name").asText();
                toolInput = block.path("input");
            }
        }
        JsonNode usage = root.path("usage");
        return LlmResponse.builder()
                .text(text.toString())
                .toolName(toolName)
                .toolInput(toolInput)
                .stopReason(root.path("stop_reason").asText(null))
                .usage(new LlmUsage(intOrNull(usage, "input_tokens"), intOrNull(usage, "output_tokens"),
                        intOrNull(usage, "cache_read_input_tokens"), intOrNull(usage, "cache_creation_input_tokens")))
                .latencyMs(latencyMs)
                .model(root.path("model").asText(request.model()))
                .requestId(requestId != null ? requestId : root.path("id").asText(null))
                .build();
    }

    private String errorType(String raw) {
        try {
            return objectMapper.readTree(raw).path("error").path("type").asText("");
        } catch (IOException e) {
            return "";
        }
    }

    // 본문은 llm.log-body=true일 때만 로컬 파일로 남긴다 (민감정보라 DB·로그에는 남기지 않음)
    private void writeBody(LlmRequest request, ObjectNode body, String raw, String requestId) {
        if (!properties.isLogBody()) {
            return;
        }
        try {
            Path dir = Path.of("build", "llm-body");
            Files.createDirectories(dir);
            String name = System.currentTimeMillis() + "-" + request.pipelineVersion() + "-"
                    + (requestId == null ? "no-id" : requestId) + ".json";
            Files.writeString(dir.resolve(name), "{\"request\":" + body + ",\"response\":" + raw + "}");
        } catch (IOException e) {
            log.warn("llm body write failed: {}", e.getMessage());
        }
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asInt();
    }

    private static Long retryAfter(String header) {
        if (header == null) {
            return null;
        }
        try {
            return Long.parseLong(header.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean hasCause(Throwable e, Class<? extends Throwable> type) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
