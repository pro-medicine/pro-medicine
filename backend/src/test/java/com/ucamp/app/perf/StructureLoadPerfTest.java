package com.ucamp.app.perf;

import tools.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.ucamp.app.domain.recommendation.perf.NotificationSpikeSimulator;
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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static com.ucamp.app.perf.LoadTestSupport.OM;

/**
 * STEP 09: 처리 구조 비교 (V9-sync / V9-vt / V9-queue) × 시나리오 (steady / spike-burst / spike-spread).
 * k6 미설치로 in-JVM 대체 측정: 실제 Tomcat(RANDOM_PORT) + H2 + WireMock(LLM 고정 지연 3초) + JDK HttpClient(가상 스레드로 요청 발사).
 * 같은 서버 자원: Tomcat 최대 스레드 50, Hikari 기본 10, 파이프라인 V4. 큐 워커 동시 처리 20.
 * 도착은 open 모델(정해진 시각에 요청 시작). 클라이언트 타임아웃 30초. 큐는 접수 후 0.5초마다 GET으로 완료를 확인한다.
 * 실행: ./gradlew perfTest --tests '*StructureLoadPerfTest*'
 */
@Tag("perf")
class StructureLoadPerfTest {
    static final int TOMCAT_THREADS = 50;
    static final int WORKERS = 20;
    static final int BURST_N = 600;
    static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(30);
    static final Duration QUEUE_DEADLINE = Duration.ofSeconds(240);
    static final WireMockServer WIREMOCK = LoadTestSupport.startWireMockWithJournal("normal");

    @AfterAll
    static void stop() {
        WIREMOCK.stop();
    }

    static void base(DynamicPropertyRegistry r) {
        LoadTestSupport.wireMockProperties(r, WIREMOCK);
        r.add("recommendation.pipeline-version", () -> "V4");
        r.add("server.tomcat.threads.max", () -> String.valueOf(TOMCAT_THREADS));
        r.add("recommendation.worker.concurrency", () -> String.valueOf(WORKERS));
        r.add("recommendation.worker.poll-timeout", () -> "200ms");
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @Import(LoadTestSupport.WireMockLlmConfig.class)
    class Sync {
        @LocalServerPort
        int port;

        @DynamicPropertySource
        static void p(DynamicPropertyRegistry r) {
            base(r);
            r.add("recommendation.api-mode", () -> "sync");
        }

        @Test
        void scenarios() throws Exception {
            runAll("V9-sync", port, false);
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @Import(LoadTestSupport.WireMockLlmConfig.class)
    class VirtualThreads {
        @LocalServerPort
        int port;

        @DynamicPropertySource
        static void p(DynamicPropertyRegistry r) {
            base(r);
            r.add("recommendation.api-mode", () -> "sync");
            r.add("spring.threads.virtual.enabled", () -> "true");
        }

        @Test
        void scenarios() throws Exception {
            runAll("V9-vt", port, false);
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @Import(LoadTestSupport.WireMockLlmConfig.class)
    class Queue {
        @LocalServerPort
        int port;

        @DynamicPropertySource
        static void p(DynamicPropertyRegistry r) {
            base(r);
            r.add("recommendation.api-mode", () -> "queue");
        }

        @Test
        void scenarios() throws Exception {
            runAll("V9-queue", port, true);
        }
    }

    // ---------- 시나리오 ----------

    static void runAll(String structure, int port, boolean queue) throws Exception {
        String base = "http://localhost:" + port;
        HttpClient client = LoadTestSupport.httpClient();
        LoadTestSupport.Requests requests = LoadTestSupport.loadRequests();
        warmup(client, base, requests, queue);

        // steady: 초당 10건 30초
        run(structure, "steady", client, base, requests, queue, uniform(300, Duration.ofSeconds(30)));
        // spike-burst: 알림 동시 발송 가정, 10초 안에 600명
        run(structure, "spike-burst", client, base, requests, queue, uniform(BURST_N, Duration.ofSeconds(10)));
        // spike-spread: 같은 600명, 발송 분산(30분)을 1/30로 줄인 60초 구간에 고르게 (NotificationSpikeSimulator)
        run(structure, "spike-spread", client, base, requests, queue, spread(BURST_N, Duration.ofSeconds(60)));
    }

    static List<Long> uniform(int n, Duration window) {
        List<Long> offsets = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            offsets.add(window.toMillis() * i / n);
        }
        return offsets;
    }

    static List<Long> spread(int n, Duration window) {
        LocalDateTime start = LocalDateTime.of(2026, 10, 21, 9, 0);
        return new NotificationSpikeSimulator()
                .plan(LongStream.rangeClosed(1, n).boxed().toList(), start, window).stream()
                .map(p -> Duration.between(start, p.scheduledAt()).toMillis())
                .toList();
    }

    static void warmup(HttpClient client, String base, LoadTestSupport.Requests requests, boolean queue) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ids.add(LoadTestSupport.submitSurvey(client, base, requests.profiles().get(i)));
        }
        CountDownLatch latch = new CountDownLatch(ids.size());
        for (long id : ids) {
            Thread.startVirtualThread(() -> {
                try {
                    HttpResponse<String> r = LoadTestSupport.postJson(client, base + "/api/v1/recommendations",
                            "{\"responseId\":" + id + "}", Duration.ofSeconds(60));
                    if (queue) {
                        awaitDone(client, base, OM.readTree(r.body()).get("data").get("recommendationId").asLong(),
                                System.nanoTime() + Duration.ofSeconds(60).toNanos());
                    }
                } catch (Exception ignored) {
                    // 워밍업 실패는 측정에 영향 없음
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(2, TimeUnit.MINUTES);
    }

    record Outcome(boolean ok, double acceptMs, double completeMs) {
    }

    static void run(String structure, String scenario, HttpClient client, String base,
                    LoadTestSupport.Requests requests, boolean queue, List<Long> offsets) throws Exception {
        int port = Integer.parseInt(base.substring(base.lastIndexOf(':') + 1));
        // 응답은 미리 만들어 둔다 (설문 제출은 측정에서 뺀다)
        List<Long> responseIds = new ArrayList<>();
        for (int i = 0; i < offsets.size(); i++) {
            responseIds.add(LoadTestSupport.submitSurvey(client, base,
                    requests.profiles().get(requests.sequence().get(i % requests.sequence().size()))));
        }
        WIREMOCK.resetRequests();
        LoadTestSupport.CountingLlmClient.resetMax();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        threads.resetPeakThreadCount();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        com.sun.management.OperatingSystemMXBean os =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        AtomicBoolean sampling = new AtomicBoolean(true);
        PerfStats cpu = new PerfStats();
        AtomicInteger maxThreads = new AtomicInteger();
        // 서버 요청 처리 스레드만 따로 센다 (같은 JVM의 WireMock Jetty 스레드를 빼기 위해): Tomcat 워커 + 큐 워커
        AtomicInteger maxServerThreads = new AtomicInteger();
        final long[] maxHeap = {0};
        Thread sampler = Thread.ofPlatform().daemon().start(() -> {
            while (sampling.get()) {
                maxThreads.accumulateAndGet(threads.getThreadCount(), Math::max);
                maxServerThreads.accumulateAndGet(serverThreadCount(port, queue), Math::max);
                maxHeap[0] = Math.max(maxHeap[0], memory.getHeapMemoryUsage().getUsed());
                double load = os.getProcessCpuLoad();
                if (load >= 0) {
                    synchronized (cpu) {
                        cpu.add(load * 100);
                    }
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });

        ConcurrentLinkedQueue<Outcome> outcomes = new ConcurrentLinkedQueue<>();
        CountDownLatch latch = new CountDownLatch(offsets.size());
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        long t0 = System.nanoTime();
        for (int i = 0; i < offsets.size(); i++) {
            long responseId = responseIds.get(i);
            scheduler.schedule(() -> Thread.startVirtualThread(() -> {
                try {
                    outcomes.add(fire(client, base, responseId, queue));
                } finally {
                    latch.countDown();
                }
            }), offsets.get(i), TimeUnit.MILLISECONDS);
        }
        latch.await(10, TimeUnit.MINUTES);
        double elapsed = (System.nanoTime() - t0) / 1e9;
        scheduler.shutdownNow();
        sampling.set(false);
        sampler.join(1000);

        PerfStats accept = new PerfStats();
        PerfStats complete = new PerfStats();
        int ok = 0;
        for (Outcome o : outcomes) {
            if (o.ok()) {
                ok++;
                complete.add(o.completeMs());
            }
            if (o.acceptMs() >= 0) {
                accept.add(o.acceptMs());
            }
        }
        int n = offsets.size();
        double errorRate = (double) (n - ok) / n;
        double tps = ok / elapsed;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("structure", structure);
        out.put("scenario", scenario);
        out.put("conditions", "in-JVM (k6 대체), Tomcat threads.max=" + TOMCAT_THREADS + ", Hikari 10, H2, WireMock 3000ms, "
                + "V4 pipeline, queue workers=" + WORKERS + ", client timeout " + CLIENT_TIMEOUT.toSeconds() + "s");
        out.put("requests", n);
        out.put("arrivalWindowMs", offsets.get(offsets.size() - 1));
        out.put("elapsedSeconds", elapsed);
        out.put("ok", ok);
        out.put("errorRate", errorRate);
        out.put("tps", tps);
        out.put("acceptLatency", accept.count() == 0 ? null : accept.summary());
        out.put("completeLatency", complete.count() == 0 ? null : complete.summary());
        out.put("maxThreads", maxThreads.get());
        out.put("peakThreads", threads.getPeakThreadCount());
        out.put("maxServerPlatformThreads", maxServerThreads.get());
        out.put("cpuPercentMax", cpu.count() == 0 ? null : PerfStats.round(cpu.max()));
        out.put("cpuPercentAvg", cpu.count() == 0 ? null : PerfStats.round(cpu.mean()));
        out.put("heapUsedMaxMb", maxHeap[0] / 1024 / 1024);
        out.put("llmConcurrentMax", LoadTestSupport.CountingLlmClient.MAX.get());
        out.put("llmCalls", LoadTestSupport.llmCalls(WIREMOCK));
        Path json = Path.of("docs/perf/runs/STEP09-" + structure + "-" + scenario + "-" + System.currentTimeMillis() + ".json");
        Files.createDirectories(json.getParent());
        Files.writeString(json, OM.writerWithDefaultPrettyPrinter().writeValueAsString(out));
        System.out.println("[LOAD09] " + OM.writeValueAsString(out));

        if ("spike-burst".equals(scenario)) {
            double p95 = complete.count() == 0 ? Double.NaN : complete.percentile(95);
            EvalRunner.appendCsv(Path.of("docs/perf/results.csv"), List.of("09", structure + "-spike-burst",
                    LocalDate.now().toString(), "wiremock", "", "", "", "", "", "", "", "", "", "", "", "", "",
                    p95, "", "", "", "", "", tps, errorRate,
                    "in-JVM 부하(k6 대체) WireMock 3s; N=" + n + " in 10s; tomcat_threads=" + TOMCAT_THREADS
                            + "; workers=" + WORKERS + "; accept_p95_ms="
                            + (accept.count() == 0 ? "" : PerfStats.round(accept.percentile(95)))
                            + "; complete_max_ms=" + (complete.count() == 0 ? "" : PerfStats.round(complete.max()))
                            + "; llm_concurrent_max=" + LoadTestSupport.CountingLlmClient.MAX.get()
                            + "; server_platform_threads_max=" + maxServerThreads.get() + "; detail=" + json.getFileName()));
        }
    }

    /**
     * 이 서버의 요청 처리 플랫폼 스레드 수: Tomcat 워커(http-nio-{port}-exec-*) + (큐 구조면) 큐 워커(rec-worker-*).
     * 같은 JVM에 캐시된 다른 테스트 컨텍스트의 스레드는 포트로 거른다. 가상 스레드는 세지 않는다.
     */
    static int serverThreadCount(int port, boolean queue) {
        int n = 0;
        String tomcat = "http-nio-" + port + "-exec-";
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            String name = t.getName();
            if (name.startsWith(tomcat) || (queue && name.startsWith("rec-worker-"))) {
                n++;
            }
        }
        return n;
    }

    static Outcome fire(HttpClient client, String base, long responseId, boolean queue) {
        long start = System.nanoTime();
        try {
            HttpResponse<String> r = LoadTestSupport.postJson(client, base + "/api/v1/recommendations",
                    "{\"responseId\":" + responseId + "}", CLIENT_TIMEOUT);
            double acceptMs = (System.nanoTime() - start) / 1_000_000.0;
            if (!queue) {
                return new Outcome(r.statusCode() == 200, acceptMs, acceptMs);
            }
            if (r.statusCode() != 202 && r.statusCode() != 200) {
                return new Outcome(false, acceptMs, -1);
            }
            long id = OM.readTree(r.body()).get("data").get("recommendationId").asLong();
            boolean done = awaitDone(client, base, id, start + QUEUE_DEADLINE.toNanos());
            return new Outcome(done, acceptMs, (System.nanoTime() - start) / 1_000_000.0);
        } catch (Exception e) {
            return new Outcome(false, -1, -1);
        }
    }

    static boolean awaitDone(HttpClient client, String base, long id, long deadlineNanos) throws Exception {
        while (System.nanoTime() < deadlineNanos) {
            HttpResponse<String> r = LoadTestSupport.get(client, base + "/api/v1/recommendations/" + id, CLIENT_TIMEOUT);
            JsonNode data = OM.readTree(r.body()).get("data");
            String status = data == null ? "" : data.get("status").asString();
            if ("DONE".equals(status)) {
                return true;
            }
            if ("FAILED".equals(status)) {
                return false;
            }
            Thread.sleep(500);
        }
        return false;
    }
}
