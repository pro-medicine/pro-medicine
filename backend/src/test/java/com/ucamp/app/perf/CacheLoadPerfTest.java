package com.ucamp.app.perf;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.ucamp.app.eval.EvalRunner;
import com.ucamp.app.support.PerfStats;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ucamp.app.perf.LoadTestSupport.OM;

/**
 * STEP 08 부하 상황의 캐시 효과: 같은 요청 세트(perf/k6/data/requests.json 2,000건)를 V4 / V6 서버에 보낸다.
 * 측정 전 워밍업 50건은 요청 세트에 한 번도 나오지 않는 조합으로 보낸다 (캐시를 미리 채우지 않게).
 * LLM = WireMock 고정 지연 3초 (perf/wiremock/normal). 동시 사용자 50명이 남은 요청을 차례로 가져가는 closed 모델.
 * 실행: ./gradlew perfTest --tests '*CacheLoadPerfTest*'  → results.csv에 08,V4-load / 08,V6-load 줄
 */
@Tag("perf")
class CacheLoadPerfTest {
    static final int REQUESTS = 2000;
    static final int WARMUP = 50;
    static final int USERS = 50;
    static final WireMockServer WIREMOCK = LoadTestSupport.startWireMockWithJournal("normal");

    @AfterAll
    static void stop() {
        WIREMOCK.stop();
    }

    static void props(DynamicPropertyRegistry r, String version) {
        LoadTestSupport.wireMockProperties(r, WIREMOCK);
        r.add("recommendation.pipeline-version", () -> version);
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @Import(LoadTestSupport.WireMockLlmConfig.class)
    class V4Load {
        @LocalServerPort
        int port;

        @DynamicPropertySource
        static void p(DynamicPropertyRegistry r) {
            props(r, "V4");
        }

        @Test
        void load() throws Exception {
            run("V4", port);
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @Import(LoadTestSupport.WireMockLlmConfig.class)
    class V6Load {
        @LocalServerPort
        int port;

        @DynamicPropertySource
        static void p(DynamicPropertyRegistry r) {
            props(r, "V6");
        }

        @Test
        void load() throws Exception {
            run("V6", port);
        }
    }

    static void run(String version, int port) throws Exception {
        WIREMOCK.resetRequests();
        LoadTestSupport.Requests requests = LoadTestSupport.loadRequests();
        String base = "http://localhost:" + port;
        HttpClient client = LoadTestSupport.httpClient();
        // 워밍업: 요청 세트에 없는 조합으로 JIT·커넥션·DB를 데운다
        java.util.Set<Integer> used = new java.util.HashSet<>(requests.sequence());
        List<Integer> unused = java.util.stream.IntStream.range(0, requests.profiles().size())
                .filter(i -> !used.contains(i)).limit(WARMUP).boxed().toList();
        ExecutorService warm = Executors.newFixedThreadPool(USERS);
        for (int idx : unused) {
            warm.submit(() -> {
                long id = LoadTestSupport.submitSurvey(client, base, requests.profiles().get(idx));
                return LoadTestSupport.postJson(client, base + "/api/v1/recommendations", "{\"responseId\":" + id + "}",
                        Duration.ofSeconds(60)).statusCode();
            });
        }
        warm.shutdown();
        warm.awaitTermination(5, TimeUnit.MINUTES);
        WIREMOCK.resetRequests();

        PerfStats recommend = new PerfStats();
        AtomicInteger next = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        List<Long> starts = new ArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(USERS);
        long t0 = System.nanoTime();
        for (int u = 0; u < USERS; u++) {
            pool.submit(() -> {
                int i;
                while ((i = next.getAndIncrement()) < REQUESTS) {
                    try {
                        long responseId = LoadTestSupport.submitSurvey(client, base,
                                requests.profiles().get(requests.sequence().get(i)));
                        long s = System.nanoTime();
                        HttpResponse<String> r = LoadTestSupport.postJson(client, base + "/api/v1/recommendations",
                                "{\"responseId\":" + responseId + "}", Duration.ofSeconds(60));
                        double ms = (System.nanoTime() - s) / 1_000_000.0;
                        synchronized (recommend) {
                            recommend.add(ms);
                        }
                        if (r.statusCode() != 200) {
                            errors.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(15, TimeUnit.MINUTES);
        double seconds = (System.nanoTime() - t0) / 1e9;
        int llmCalls = LoadTestSupport.llmCalls(WIREMOCK);
        double tps = REQUESTS / seconds;
        double errorRate = (double) errors.get() / REQUESTS;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("label", version + "-load");
        out.put("conditions", "in-JVM load test (k6 미설치로 대체), embedded Tomcat, H2, WireMock 고정 지연 3000ms, "
                + REQUESTS + " requests, " + USERS + " closed-model users, warmup " + WARMUP + " (unused profiles)");
        out.put("durationSeconds", seconds);
        out.put("tps", tps);
        out.put("errorRate", errorRate);
        out.put("llmCalls", llmCalls);
        out.put("distinctProfiles", requests.sequence().subList(0, REQUESTS).stream().distinct().count());
        out.put("recommendLatency", recommend.summary());
        Path json = Path.of("docs/perf/runs/STEP08-load-" + version + "-" + System.currentTimeMillis() + ".json");
        Files.createDirectories(json.getParent());
        Files.writeString(json, OM.writerWithDefaultPrettyPrinter().writeValueAsString(out));
        System.out.println("[LOAD] " + OM.writeValueAsString(out));

        List<Object> row = new ArrayList<>(List.of("08", version + "-load-wiremock-2000", LocalDate.now().toString(),
                "wiremock", "", "", "", "", "", "", "", "", "", "", "", "", "", recommend.percentile(95),
                "", "", "", "", "", tps, errorRate,
                "in-JVM 부하(k6 대체) WireMock 3s; requests=" + REQUESTS + "; warmup=" + WARMUP + "; users=" + USERS + "; llm_calls=" + llmCalls
                        + "; p50_ms=" + PerfStats.round(recommend.percentile(50)) + "; detail=" + json.getFileName()));
        EvalRunner.appendCsv(Path.of("docs/perf/results.csv"), row);
    }
}
