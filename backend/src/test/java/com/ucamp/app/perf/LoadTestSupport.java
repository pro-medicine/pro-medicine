package com.ucamp.app.perf;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.ucamp.app.infra.llm.ChatProviderSupport;
import com.ucamp.app.infra.llm.SpringAiLlmClient;
import com.ucamp.app.infra.llm.LlmClient;
import com.ucamp.app.infra.llm.LlmRequest;
import com.ucamp.app.infra.llm.LlmResponse;
import com.ucamp.app.infra.llm.LlmProperties;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.support.PerfStats;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * k6 대체 로컬 부하 측정 지원 (k6 미설치). 실제 Tomcat(RANDOM_PORT) + 프로세스 안 WireMock(LLM 대체) + JDK HttpClient.
 * 실제 OpenAI API는 부르지 않는다. 결과는 같은 JVM에서 부하 발생기와 서버가 CPU를 나눠 쓰는 조건이다.
 */
public final class LoadTestSupport {
    public static final ObjectMapper OM = new ObjectMapper();

    private LoadTestSupport() {
    }

    /** perf/wiremock/{scenario} 매핑 파일로 WireMock을 띄운다. 지연 응답이 스레드를 잡지 않게 비동기 응답을 켠다 */
    public static WireMockServer startWireMock(String scenario) {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options()
                .dynamicPort()
                .usingFilesUnderDirectory(Path.of("perf/wiremock", scenario).toString())
                .asynchronousResponseEnabled(true)
                .asynchronousResponseThreads(200)
                .containerThreads(800)
                .jettyAcceptQueueSize(2000)
                .disableRequestJournal());
        server.start();
        return server;
    }

    public static WireMockServer startWireMockWithJournal(String scenario) {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options()
                .dynamicPort()
                .usingFilesUnderDirectory(Path.of("perf/wiremock", scenario).toString())
                .asynchronousResponseEnabled(true)
                .asynchronousResponseThreads(200)
                .containerThreads(800)
                .jettyAcceptQueueSize(2000));
        server.start();
        return server;
    }

    /** Spring AI(OpenAI) 자동 설정을 WireMock으로 향하게 한다 */
    public static void wireMockProperties(org.springframework.test.context.DynamicPropertyRegistry r, WireMockServer server) {
        r.add("spring.ai.model.chat", () -> "openai");
        r.add("OPENAI_API_KEY", () -> "dummy-key");
        r.add("spring.ai.openai.base-url", () -> "http://localhost:" + server.port() + "/v1");
    }

    public static int llmCalls(WireMockServer server) {
        return server.countRequestsMatching(postRequestedFor(urlPathEqualTo("/v1/chat/completions")).build()).getCount();
    }

    /**
     * 원본 LLM 클라이언트(fakeLlmClient 빈)를 "SpringAiLlmClient + Spring AI가 만든 ChatModel(WireMock 주소, 가짜 키)"로 바꾼다 (테스트 전용).
     * 각 부하 테스트가 spring.ai.model.chat=openai와 spring.ai.openai.base-url(WireMock)을 넣는다 ({@link #wireMockProperties}).
     * llm.provider는 fake 그대로 두고 빈 후처리로 바꾸는 이유: LLM 동시 호출 수를 세는 CountingLlmClient를 원본 자리에 끼우기 위해서다.
     */
    @TestConfiguration
    public static class WireMockLlmConfig {
        @Bean
        public static BeanPostProcessor wireMockLlmClientReplacer(ObjectProvider<LlmProperties> properties,
                                                                  ObjectProvider<ObjectMapper> objectMapper,
                                                                  ObjectProvider<ChatModel> chatModel,
                                                                  ObjectProvider<ChatProviderSupport> provider) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if ("fakeLlmClient".equals(beanName)) {
                        LlmClient http = new SpringAiLlmClient(chatModel.getObject(), provider.getObject(),
                                properties.getObject(), objectMapper.getObject());
                        return new WireMockBackedLlmClient(objectMapper.getObject(), new CountingLlmClient(http));
                    }
                    return bean;
                }
            };
        }
    }

    /** FakeLlmClient 자리에 들어가 실제로는 WireMock으로 HTTP 호출을 보낸다 */
    static final class WireMockBackedLlmClient extends FakeLlmClient {
        private final LlmClient delegate;

        WireMockBackedLlmClient(ObjectMapper objectMapper, LlmClient delegate) {
            super(objectMapper);
            this.delegate = delegate;
        }

        @Override
        public LlmResponse call(LlmRequest request) {
            return delegate.call(request);
        }
    }

    /** LLM 동시 호출 수 측정용 데코레이터 (테스트 전용) */
    public static final class CountingLlmClient implements LlmClient {
        public static final AtomicInteger IN_FLIGHT = new AtomicInteger();
        public static final AtomicInteger MAX = new AtomicInteger();
        private final LlmClient delegate;

        CountingLlmClient(LlmClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public LlmResponse call(LlmRequest request) {
            int now = IN_FLIGHT.incrementAndGet();
            MAX.accumulateAndGet(now, Math::max);
            try {
                return delegate.call(request);
            } finally {
                IN_FLIGHT.decrementAndGet();
            }
        }

        public static void resetMax() {
            MAX.set(IN_FLIGHT.get());
        }
    }

    public static HttpClient httpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public record Requests(List<JsonNode> profiles, List<Integer> sequence, JsonNode distribution) {
    }

    public static Requests loadRequests() throws IOException {
        JsonNode root = OM.readTree(Files.readString(Path.of("perf/k6/data/requests.json")));
        List<JsonNode> profiles = new ArrayList<>();
        root.get("profiles").forEach(profiles::add);
        List<Integer> sequence = new ArrayList<>();
        root.get("sequence").forEach(n -> sequence.add(n.asInt()));
        return new Requests(profiles, sequence, root.get("distribution"));
    }

    public static HttpResponse<String> postJson(HttpClient client, String url, String body, Duration timeout)
            throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    public static HttpResponse<String> get(HttpClient client, String url, Duration timeout)
            throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** 비회원 설문 제출 → responseId */
    public static long submitSurvey(HttpClient client, String base, JsonNode answers) throws Exception {
        HttpResponse<String> r = postJson(client, base + "/api/v1/surveys/1/responses",
                OM.writeValueAsString(Map.of("answers", answers)), Duration.ofSeconds(60));
        if (r.statusCode() != 200) {
            throw new IllegalStateException("survey " + r.statusCode());
        }
        return OM.readTree(r.body()).get("data").get("responseId").asLong();
    }

    public static Map<String, Object> summary(PerfStats stats) {
        return stats.summary();
    }
}
