package com.ucamp.app.infra.llm;

import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogRepository;
import com.ucamp.app.infra.llm.log.LlmCallLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LoggingLlmClientTest {
    @Autowired
    LlmCallLogService logService;
    @Autowired
    LlmCallLogRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void 성공_호출을_기록하고_행_ID를_돌려준다() {
        LlmClient delegate = req -> LlmResponse.builder().text("ok").model("claude-opus-5-5")
                .usage(new LlmUsage(100, 20, 5, 0)).latencyMs(1234).build();
        LoggingLlmClient client = new LoggingLlmClient(delegate, logService);

        LlmResponse res = client.call(request("eval-1"));

        LlmCallLog log = repository.findById(res.callLogId()).orElseThrow();
        assertThat(log.getOutcome()).isEqualTo(LlmCallLog.SUCCESS);
        assertThat(log.getInputTokens()).isEqualTo(100);
        assertThat(log.getOutputTokens()).isEqualTo(20);
        assertThat(log.getCacheReadTokens()).isEqualTo(5);
        assertThat(log.getLatencyMs()).isGreaterThanOrEqualTo(1234);
        assertThat(log.getEvalRunId()).isEqualTo("eval-1");
        assertThat(log.getPipelineVersion()).isEqualTo("V0");
        assertThat(log.getPromptVersion()).isEqualTo("v0.1");
        assertThat(log.getCallTag()).isEqualTo("RECOMMEND");
        assertThat(log.getAttemptNo()).isEqualTo(1);
        assertThat(log.getHttpStatus()).isEqualTo(200);
    }

    @Test
    void 실패_호출도_기록한다_HTTP_오류와_타임아웃() {
        LoggingLlmClient httpError = new LoggingLlmClient(req -> {
            throw new LlmCallException(LlmCallException.Kind.HTTP_ERROR, 503, null, 50, "x", null);
        }, logService);
        LoggingLlmClient timeout = new LoggingLlmClient(req -> {
            throw new LlmCallException(LlmCallException.Kind.TIMEOUT, null, null, 30000, "t", null);
        }, logService);

        assertThatThrownBy(() -> httpError.call(request("eval-2")))
                .isInstanceOfSatisfying(LlmCallException.class, e -> assertThat(e.getCallLogId()).isNotNull());
        assertThatThrownBy(() -> timeout.call(request("eval-2"))).isInstanceOf(LlmCallException.class);

        List<LlmCallLog> logs = repository.findByEvalRunIdOrderByLlmCallLogIdAsc("eval-2");
        assertThat(logs).extracting(LlmCallLog::getOutcome).containsExactly(LlmCallLog.HTTP_ERROR, LlmCallLog.TIMEOUT);
        assertThat(logs.get(0).getHttpStatus()).isEqualTo(503);
        assertThat(logs.get(1).getLatencyMs()).isGreaterThanOrEqualTo(30000);
    }

    @Test
    void 파이프라인이_outcome을_바꿀_수_있다() {
        LoggingLlmClient client = new LoggingLlmClient(req -> LlmResponse.builder().text("x").model("m").build(), logService);
        LlmResponse res = client.call(request(null));

        logService.markOutcome(res.callLogId(), LlmCallLog.PARSE_FAIL);

        assertThat(repository.findById(res.callLogId()).orElseThrow().getOutcome()).isEqualTo(LlmCallLog.PARSE_FAIL);
    }

    private static LlmRequest request(String evalRunId) {
        return LlmRequest.builder().model("claude-opus-5-5").system("s").messages(List.of(LlmMessage.user("u")))
                .maxTokens(10).callTag(LlmRequest.TAG_RECOMMEND).pipelineVersion("V0").promptVersion("v0.1")
                .evalRunId(evalRunId).build();
    }
}
