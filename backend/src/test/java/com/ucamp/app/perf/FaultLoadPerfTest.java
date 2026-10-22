package com.ucamp.app.perf;

import tools.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.ucamp.app.domain.recommendation.queue.RecommendationQueue;
import com.ucamp.app.eval.EvalRunner;
import com.ucamp.app.infra.llm.ResilientLlmClient;
import com.ucamp.app.infra.llm.log.LlmCallLog;
import com.ucamp.app.infra.llm.log.LlmCallLogRepository;
import com.ucamp.app.support.PerfStats;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static com.ucamp.app.perf.LoadTestSupport.OM;

/**
 * STEP 10 장애 주입: V9-queue(장애 대응 없음) vs V10(재시도·서킷·Bulkhead·폴백). 둘 다 queue 모드, 워커 20.
 * LLM = WireMock (perf/wiremock/{normal,slow,flaky,outage}, gen-scenarios.js). k6 미설치로 in-JVM 대체 측정.
 * 부하: normal·slow·flaky = 30초 동안 200건(초당 6.7건 ≈ 워커 20 × 3초 처리량), outage = 120초 동안 600건, 처음 60초는 100% 500.
 * 실행: ./gradlew perfTest --tests '*FaultLoadPerfTest*'
 */
@Tag("perf")
class FaultLoadPerfTest {
    static final int WORKERS = 20;
    static final Duration DEADLINE = Duration.ofSeconds(300);
    static final WireMockServer WIREMOCK = LoadTestSupport.startWireMockWithJournal("normal");

    @AfterAll
    static void stop() {
        WIREMOCK.stop();
    }

    static void base(DynamicPropertyRegistry r) {
        LoadTestSupport.wireMockProperties(r, WIREMOCK);
        r.add("recommendation.pipeline-version", () -> "V4");
        r.add("recommendation.api-mode", () -> "queue");
        r.add("recommendation.worker.concurrency", () -> String.valueOf(WORKERS));
        r.add("recommendation.worker.poll-timeout", () -> "200ms");
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @Import(LoadTestSupport.WireMockLlmConfig.class)
    class V9Queue {
        @LocalServerPort
        int port;
        @Autowired
        LlmCallLogRepository logs;
        @Autowired
        RecommendationQueue queue;

        @DynamicPropertySource
        static void p(DynamicPropertyRegistry r) {
            base(r);
            r.add("llm.timeout-ms", () -> "30000");
        }

        @Test
        void scenarios() throws Exception {
            runAll("V9-queue", port, logs, queue, null);
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @Import(LoadTestSupport.WireMockLlmConfig.class)
    class V10 {
        @LocalServerPort
        int port;
        @Autowired
        LlmCallLogRepository logs;
        @Autowired
        RecommendationQueue queue;
        @Autowired
        CircuitBreakerRegistry circuitBreakers;

        @DynamicPropertySource
        static void p(DynamicPropertyRegistry r) {
            base(r);
            r.add("llm.timeout-ms", () -> "15000");
            r.add("llm.resilience.enabled", () -> "true");
            r.add("llm.resilience.bulkhead-max-concurrent", () -> String.valueOf(WORKERS));
        }

        @Test
        void scenarios() throws Exception {
            runAll("V10", port, logs, queue, circuitBreakers.circuitBreaker(ResilientLlmClient.RESILIENCE_NAME));
        }
    }

    static void runAll(String structure, int port, LlmCallLogRepository logs, RecommendationQueue queue,
                       CircuitBreaker cb) throws Exception {
        String base = "http://localhost:" + port;
        HttpClient client = LoadTestSupport.httpClient();
        LoadTestSupport.Requests requests = LoadTestSupport.loadRequests();
        load("normal");
        StructureLoadPerfTest.warmup(client, base, requests, true);
        for (String scenario : List.of("normal", "slow", "flaky")) {
            load(scenario);
            run(structure, scenario, client, base, requests, logs, queue, cb, StructureLoadPerfTest.uniform(200,
                    Duration.ofSeconds(30)), Duration.ZERO);
            if (cb != null) {
                cb.reset();
            }
        }
        load("outage");
        run(structure, "outage", client, base, requests, logs, queue, cb,
                StructureLoadPerfTest.uniform(600, Duration.ofSeconds(120)), Duration.ofSeconds(60));
    }

    /** perf/wiremock/{scenario}/mappings 의 매핑으로 바꾼다 (시나리오 상태도 처음으로) */
    static void load(String scenario) throws Exception {
        WIREMOCK.resetAll();
        try (Stream<Path> files = Files.list(Path.of("perf/wiremock", scenario, "mappings"))) {
            for (Path f : files.sorted().toList()) {
                WIREMOCK.addStubMapping(StubMapping.buildFrom(Files.readString(f)));
            }
        }
    }

    record Outcome(String status, boolean fallback, double completeMs, long finishedAtNanos) {
    }

    static void run(String structure, String scenario, HttpClient client, String base,
                    LoadTestSupport.Requests requests, LlmCallLogRepository logs, RecommendationQueue queue,
                    CircuitBreaker cb, List<Long> offsets, Duration outage) throws Exception {
        List<Long> responseIds = new ArrayList<>();
        for (int i = 0; i < offsets.size(); i++) {
            responseIds.add(LoadTestSupport.submitSurvey(client, base,
                    requests.profiles().get(requests.sequence().get(i % requests.sequence().size()))));
        }
        long logIdBefore = logs.findAll().stream().mapToLong(LlmCallLog::getLlmCallLogId).max().orElse(0);
        AtomicLong circuitOpenAt = new AtomicLong(-1);
        AtomicLong circuitClosedAt = new AtomicLong(-1);
        if (cb != null) {
            cb.getEventPublisher().onStateTransition(e -> {
                if (e.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                    circuitOpenAt.compareAndSet(-1, System.nanoTime());
                }
                if (e.getStateTransition().getToState() == CircuitBreaker.State.CLOSED && circuitOpenAt.get() > 0) {
                    circuitClosedAt.set(System.nanoTime());
                }
            });
        }
        AtomicBoolean sampling = new AtomicBoolean(true);
        AtomicLong maxBacklog = new AtomicLong();
        Thread sampler = Thread.ofPlatform().daemon().start(() -> {
            while (sampling.get()) {
                maxBacklog.accumulateAndGet(queue.size(), Math::max);
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });

        ConcurrentLinkedQueue<Outcome> outcomes = new ConcurrentLinkedQueue<>();
        CountDownLatch latch = new CountDownLatch(offsets.size());
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        long t0 = System.nanoTime();
        AtomicLong outageEndedAt = new AtomicLong(-1);
        if (!outage.isZero()) {
            scheduler.schedule(() -> {
                WIREMOCK.removeStubMapping(WIREMOCK.getStubMapping(UUID.fromString("00000000-0000-0000-0000-000000000500")).getItem());
                outageEndedAt.set(System.nanoTime());
            }, outage.toMillis(), TimeUnit.MILLISECONDS);
        }
        for (int i = 0; i < offsets.size(); i++) {
            long responseId = responseIds.get(i);
            scheduler.schedule(() -> Thread.startVirtualThread(() -> {
                try {
                    outcomes.add(fire(client, base, responseId));
                } finally {
                    latch.countDown();
                }
            }), offsets.get(i), TimeUnit.MILLISECONDS);
        }
        latch.await(15, TimeUnit.MINUTES);
        double elapsed = (System.nanoTime() - t0) / 1e9;
        scheduler.shutdownNow();
        sampling.set(false);
        sampler.join(1000);

        int n = offsets.size();
        long done = outcomes.stream().filter(o -> "DONE".equals(o.status())).count();
        long fallback = outcomes.stream().filter(o -> "DONE".equals(o.status()) && o.fallback()).count();
        PerfStats complete = new PerfStats();
        outcomes.stream().filter(o -> "DONE".equals(o.status())).forEach(o -> complete.add(o.completeMs()));
        List<LlmCallLog> scenarioLogs = logs.findAll().stream().filter(l -> l.getLlmCallLogId() > logIdBefore).toList();
        long retryCalls = scenarioLogs.stream().filter(l -> l.getAttemptNo() >= 2).count();
        long http429 = scenarioLogs.stream().filter(l -> Integer.valueOf(429).equals(l.getHttpStatus())).count();
        // 복구 후 정상화: 장애 종료 뒤 처음으로 폴백이 아닌 DONE이 끝난 시각
        Double recoveryAfterOutageMs = null;
        if (outageEndedAt.get() > 0) {
            recoveryAfterOutageMs = outcomes.stream()
                    .filter(o -> "DONE".equals(o.status()) && !o.fallback() && o.finishedAtNanos() > outageEndedAt.get())
                    .mapToLong(Outcome::finishedAtNanos).min()
                    .stream().mapToDouble(t -> (t - outageEndedAt.get()) / 1_000_000.0).boxed().findFirst().orElse(null);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("structure", structure);
        out.put("scenario", scenario);
        out.put("conditions", "in-JVM (k6 대체), queue mode, workers=" + WORKERS + ", H2, WireMock " + scenario
                + ", V4 pipeline, LLM timeout " + ("V10".equals(structure) ? "15s + retry(3)/circuit/bulkhead/fallback" : "30s, no retry"));
        out.put("requests", n);
        out.put("elapsedSeconds", elapsed);
        out.put("successRateWithFallback", (double) done / n);
        out.put("successRateWithoutFallback", (double) (done - fallback) / n);
        out.put("fallbackRate", (double) fallback / n);
        out.put("failedOrTimedOut", n - done);
        out.put("completeLatency", complete.count() == 0 ? null : complete.summary());
        out.put("llmCalls", scenarioLogs.size());
        out.put("retryCalls", retryCalls);
        out.put("http429", http429);
        out.put("queueBacklogMax", maxBacklog.get());
        out.put("circuitOpenAfterMs", circuitOpenAt.get() < 0 ? null : (circuitOpenAt.get() - t0) / 1_000_000.0);
        out.put("circuitClosedAfterOutageEndMs", circuitClosedAt.get() < 0 || outageEndedAt.get() < 0 ? null
                : (circuitClosedAt.get() - outageEndedAt.get()) / 1_000_000.0);
        out.put("firstNormalResultAfterOutageEndMs", recoveryAfterOutageMs);
        Path json = Path.of("docs/perf/runs/STEP10-" + structure + "-" + scenario + "-" + System.currentTimeMillis() + ".json");
        Files.createDirectories(json.getParent());
        Files.writeString(json, OM.writerWithDefaultPrettyPrinter().writeValueAsString(out));
        System.out.println("[LOAD10] " + OM.writeValueAsString(out));

        EvalRunner.appendCsv(Path.of("docs/perf/results.csv"), List.of("10", structure + "-" + scenario,
                LocalDate.now().toString(), "wiremock", "", "", "", "", "", "", "", "", "", "", "", "", "",
                complete.count() == 0 ? Double.NaN : complete.percentile(95), "", "", "", "", "", (double) done / elapsed,
                1.0 - (double) done / n,
                "in-JVM 부하(k6 대체) WireMock " + scenario + "; N=" + n + "; success_with_fallback=" + PerfStats.round((double) done / n)
                        + "; success_without_fallback=" + PerfStats.round((double) (done - fallback) / n)
                        + "; retry_calls=" + retryCalls + "; http429=" + http429 + "; backlog_max=" + maxBacklog.get()
                        + "; detail=" + json.getFileName()));
    }

    static Outcome fire(HttpClient client, String base, long responseId) {
        long start = System.nanoTime();
        try {
            HttpResponse<String> r = LoadTestSupport.postJson(client, base + "/api/v1/recommendations",
                    "{\"responseId\":" + responseId + "}", Duration.ofSeconds(30));
            if (r.statusCode() != 202 && r.statusCode() != 200) {
                return new Outcome("ERROR", false, -1, System.nanoTime());
            }
            long id = OM.readTree(r.body()).get("data").get("recommendationId").asLong();
            long deadline = start + DEADLINE.toNanos();
            while (System.nanoTime() < deadline) {
                HttpResponse<String> g = LoadTestSupport.get(client, base + "/api/v1/recommendations/" + id,
                        Duration.ofSeconds(30));
                JsonNode data = OM.readTree(g.body()).get("data");
                String status = data.get("status").asString();
                if ("DONE".equals(status) || "FAILED".equals(status)) {
                    long now = System.nanoTime();
                    return new Outcome(status, data.path("fallback").asBoolean(false), (now - start) / 1_000_000.0, now);
                }
                Thread.sleep(500);
            }
            return new Outcome("TIMEOUT", false, -1, System.nanoTime());
        } catch (Exception e) {
            return new Outcome("ERROR", false, -1, System.nanoTime());
        }
    }
}
