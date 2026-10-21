package com.ucamp.app.infra.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Claude API 연결 확인 (비용 발생, 호출 1회). 기본 테스트에서 제외되고 ./gradlew liveTest 로만 실행한다.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AnthropicLlmClientLiveTest {

    @Test
    void 실제_API_한_번_호출() {
        LlmProperties props = new LlmProperties();
        AnthropicLlmClient client = new AnthropicLlmClient(props, new ObjectMapper());

        LlmResponse res = client.call(LlmRequest.builder()
                .model(props.getModel())
                .messages(List.of(LlmMessage.user("한 단어로만 답해줘: 하늘 색은?")))
                .maxTokens(1000)
                .build());

        System.out.println("[LIVE] model=" + res.model() + " latencyMs=" + res.latencyMs()
                + " input=" + res.usage().inputTokens() + " output=" + res.usage().outputTokens());
        assertThat(res.usage().inputTokens()).isPositive();
    }
}
