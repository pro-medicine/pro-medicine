package com.ucamp.app.perf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.ucamp.app.infra.llm.AnthropicLlmClient;
import com.ucamp.app.infra.llm.LlmProperties;
import com.ucamp.app.infra.llm.LoggingLlmClient;
import com.ucamp.app.infra.llm.log.LlmCallLogService;
import com.ucamp.app.support.PerfStats;
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

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * k6 대체 로컬 부하 측정 지원 (k6 미설치). 실제 Tomcat(RANDOM_PORT) + 프로세스 안 WireMock(LLM 대체) + JDK HttpClient.
 * 실제 Claude API는 부르지 않는다. 결과는 같은 JVM에서 부하 발생기와 서버가 CPU를 나눠 쓰는 조건이다.
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

    public static int llmCalls(WireMockServer server) {
        return server.countRequestsMatching(postRequestedFor(urlPathEqualTo("/v1/messages")).build()).getCount();
    }

    /**
     * llmClient 빈을 "실제 Anthropic HTTP 클라이언트 + WireMock 주소 + 가짜 API 키"로 바꾼다 (테스트 전용).
     * API 키를 환경변수로만 읽는 운영 코드를 바꾸지 않으려고 빈 후처리로 교체한다.
     */
    @TestConfiguration
    public static class WireMockLlmConfig {
        @Bean
        public static BeanPostProcessor wireMockLlmClientReplacer(ObjectProvider<LlmProperties> properties,
                                                                  ObjectProvider<ObjectMapper> objectMapper,
                                                                  ObjectProvider<LlmCallLogService> logService) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if ("llmClient".equals(beanName)) {
                        return new LoggingLlmClient(new AnthropicLlmClient(properties.getObject(),
                                objectMapper.getObject(), () -> "dummy-key"), logService.getObject());
                    }
                    return bean;
                }
            };
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
