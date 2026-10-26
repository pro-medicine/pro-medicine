package com.ucamp.app.infra.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LlmClient의 실제 구현: Spring AI ChatModel(설정한 회사의 모델)로 호출한다.
 * - 연결(API 키·주소·타임아웃·재시도 횟수)은 Spring AI가 spring.ai.* 설정으로 만든 ChatModel의 기본 옵션을 그대로 쓴다
 * - 요청마다 모델·출력 토큰·구조화 출력 스키마만 정한다. 회사마다 다른 부분은 ChatProviderSupport가 맡는다
 * - 출력 형식 강제: LlmRequest의 출력 스키마를 구조화 출력(outputSchema)으로 보내고, 응답 JSON을 output에 넣는다.
 *   파이프라인(StructuredPipeline)이 output을 검증·재요청한다
 * - 재시도는 여기서 하지 않는다 (spring.ai.openai.max-retries=0). 재시도·서킷 브레이커는 ResilientLlmClient
 */
@Slf4j
public class SpringAiLlmClient implements LlmClient {
    private final ChatModel chatModel;
    private final ChatProviderSupport provider;
    private final LlmProperties properties;
    private final ObjectMapper objectMapper;

    public SpringAiLlmClient(ChatModel chatModel, ChatProviderSupport provider, LlmProperties properties,
                             ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.provider = provider;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public LlmResponse call(LlmRequest request) {
        Prompt prompt = new Prompt(messages(request), options(request));
        long start = System.nanoTime();
        try {
            LlmResponse result = parse(chatModel.call(prompt), LlmTiming.elapsedMs(start), request);
            writeBody(request, result);
            return result;
        } catch (LlmCallException e) {
            throw e;
        } catch (RuntimeException e) {
            long latency = LlmTiming.elapsedMs(start);
            LlmCallException translated = provider.translate(e, latency);
            throw translated != null ? translated
                    : new LlmCallException(LlmCallException.Kind.NETWORK, null, null, latency, String.valueOf(e.getMessage()), e);
        }
    }

    List<Message> messages(LlmRequest request) {
        List<Message> messages = new ArrayList<>();
        if (request.system() != null && !request.system().isBlank()) {
            messages.add(new SystemMessage(request.system()));
        }
        for (LlmMessage m : request.messages()) {
            messages.add(new UserMessage(m.text()));
        }
        return messages;
    }

    ChatOptions options(LlmRequest request) {
        // 기본 옵션(spring.ai.* 설정: 키·주소·타임아웃·재시도)을 복사해서 요청별 값만 덮어쓴다
        ChatOptions.Builder<?> b = chatModel.getOptions().mutate()
                .model(request.model() != null ? request.model() : properties.getModel());
        provider.maxOutputTokens(b, request.maxTokens() > 0 ? request.maxTokens() : properties.getMaxTokens());
        if (request.reasoningEffort() != null) {
            provider.reasoningEffort(b, request.reasoningEffort());
        }
        LlmOutputSchema outputSchema = request.outputSchema();
        if (outputSchema != null) {
            String schema = objectMapper.writeValueAsString(outputSchema.schema());
            if (b instanceof StructuredOutputChatOptions.Builder<?> structured) {
                structured.outputSchema(schema);
            }
            provider.structuredOutput(b, schema, outputSchema.strict());
        }
        if (request.promptCache()) {
            // 같은 파이프라인·프롬프트 버전 = 같은 고정 부분
            provider.promptCache(b, request.pipelineVersion() + ":" + request.promptVersion());
        }
        return b.build();
    }

    LlmResponse parse(ChatResponse response, long latencyMs, LlmRequest request) {
        Generation generation = response.getResult();
        String text = generation == null || generation.getOutput().getText() == null ? "" : generation.getOutput().getText();
        boolean structured = request.outputSchema() != null;
        JsonNode output = null;
        if (structured && !text.isBlank()) {
            try {
                output = objectMapper.readTree(text);
            } catch (JacksonException e) {
                // 형식이 깨진 응답은 output 없이 넘겨 파이프라인의 재요청(repair)이 처리한다
                log.debug("structured output parse failed: {}", e.getOriginalMessage());
            }
        }
        ChatResponseMetadata meta = response.getMetadata();
        return LlmResponse.builder()
                .text(structured ? "" : text)
                .output(output)
                .stopReason(generation == null ? null : generation.getMetadata().getFinishReason())
                .usage(usage(meta.getUsage(), provider.inputTokensIncludeCache()))
                .latencyMs(latencyMs)
                .model(meta.getModel() != null && !meta.getModel().isBlank() ? meta.getModel() : request.model())
                .requestId(meta.getId())
                .build();
    }

    // llm_call_logs.input_tokens는 캐시 제외분이다 (비용 계산 EvalCost와 맞춤). 포함해서 주는 회사는 빼서 기록한다
    static LlmUsage usage(Usage usage, boolean inputIncludesCache) {
        if (usage == null || usage.getPromptTokens() == null) {
            return LlmUsage.empty();
        }
        int read = toInt(usage.getCacheReadInputTokens());
        int write = toInt(usage.getCacheWriteInputTokens());
        int input = inputIncludesCache ? Math.max(0, usage.getPromptTokens() - read - write) : usage.getPromptTokens();
        return new LlmUsage(input, usage.getCompletionTokens(), read, write);
    }

    private static int toInt(Long v) {
        return v == null ? 0 : v.intValue();
    }

    // 본문은 llm.log-body=true일 때만 로컬 파일로 남긴다 (민감정보라 DB·로그에는 남기지 않음)
    private void writeBody(LlmRequest request, LlmResponse response) {
        if (!properties.isLogBody()) {
            return;
        }
        try {
            Path dir = Path.of("build", "llm-body");
            Files.createDirectories(dir);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", request.model());
            body.put("system", request.system());
            body.put("messages", request.messages());
            body.put("text", response.text());
            body.put("output", response.output());
            String name = System.currentTimeMillis() + "-" + request.pipelineVersion() + "-"
                    + (response.requestId() == null ? "no-id" : response.requestId()) + ".json";
            Files.writeString(dir.resolve(name), objectMapper.writeValueAsString(body));
        } catch (IOException | JacksonException e) {
            log.warn("llm body write failed: {}", e.getMessage());
        }
    }
}
