package com.ucamp.app.infra.llm;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

// WireMock용 OpenAI Chat Completions 응답 (테스트 전용)
final class OpenAiStubs {
    private static final ObjectMapper OM = new ObjectMapper();

    private OpenAiStubs() {
    }

    static MappingBuilder ok(String content) {
        return ok(content, 120, 30, 0);
    }

    static MappingBuilder ok(String content, int promptTokens, int completionTokens, int cachedTokens) {
        return post(urlEqualTo("/v1/chat/completions")).willReturn(okResponse(content, promptTokens, completionTokens, cachedTokens));
    }

    static ResponseDefinitionBuilder okResponse(String content, int promptTokens, int completionTokens, int cachedTokens) {
        ObjectNode body = OM.createObjectNode();
        body.put("id", "chatcmpl-test");
        body.put("object", "chat.completion");
        body.put("created", 1761100000);
        body.put("model", "gpt-6.1-sol");
        ObjectNode choice = body.putArray("choices").addObject();
        choice.put("index", 0);
        ObjectNode message = choice.putObject("message");
        message.put("role", "assistant");
        message.put("content", content);
        message.putNull("refusal");
        choice.put("finish_reason", "stop");
        ObjectNode usage = body.putObject("usage");
        usage.put("prompt_tokens", promptTokens);
        usage.put("completion_tokens", completionTokens);
        usage.put("total_tokens", promptTokens + completionTokens);
        usage.putObject("prompt_tokens_details").put("cached_tokens", cachedTokens);
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body.toString());
    }

    static MappingBuilder error(int status, String code, String retryAfter) {
        ResponseDefinitionBuilder r = aResponse().withStatus(status).withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"message\":\"injected\",\"type\":\"server_error\",\"param\":null,\"code\":\"" + code + "\"}}");
        if (retryAfter != null) {
            r.withHeader("retry-after", retryAfter);
        }
        return post(urlEqualTo("/v1/chat/completions")).willReturn(r);
    }
}
