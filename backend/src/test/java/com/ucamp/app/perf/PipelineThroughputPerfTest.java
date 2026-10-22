package com.ucamp.app.perf;

import com.ucamp.app.domain.recommendation.cache.ResultCacheService;
import com.ucamp.app.domain.recommendation.pipeline.PipelineRegistry;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import com.ucamp.app.domain.recommendation.pipeline.RecommendationPipeline;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.FakeLlmClient.FakeAnswer;
import com.ucamp.app.support.PerfStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 파이프라인 코드 자체의 처리 시간 (Fake LLM, 네트워크 대기 없음). 리팩터링 전후 성능 비교용.
 * 버전마다 워밍업 후 같은 입력으로 run()을 반복해 1회 시간(ms)의 평균·p50·p95를 build/perf/에 남긴다.
 * 실행: ./gradlew perfTest --tests '*PipelineThroughputPerfTest*' -PperfLabel=before
 */
@Tag("perf")
@SpringBootTest
class PipelineThroughputPerfTest {
    private static final int WARMUP = 500;
    private static final int RUNS = 3000;
    private static final List<String> VERSIONS = List.of("V0", "V1", "V2", "V3", "V4", "V6", "V7", "V8", "RULE_ONLY");

    @Autowired
    PipelineRegistry registry;
    @Autowired
    FakeLlmClient fake;
    @Autowired
    ResultCacheService resultCacheService;

    private final RecommendationInput input = RecommendationInput.builder()
            .respondentType("MEMBER").gender("M").birthYear(Year.now().getValue() - 40).smoking("ONE_PACK")
            .caffeine("HIGH").sunExposure("LOW").conditions(List.of("HYPERTENSION"))
            .medications(List.of(new RecommendationInput.Medication("D-003", "ACE_INHIBITOR", "에날라프릴")))
            .currentIntake(List.of(new RecommendationInput.Intake("VITAMIN_C", new BigDecimal("25"), "mg")))
            .build();

    @AfterEach
    void reset() {
        fake.reset();
        resultCacheService.clear();
    }

    @Test
    void 버전별_파이프라인_1회_처리_시간() throws Exception {
        fake.answerWith(new FakeAnswer(List.of(
                new FakeAnswer.Item("VITAMIN_C", "비타민 C", "흡연", new BigDecimal("100"), "mg"),
                new FakeAnswer.Item("VITAMIN_D", "비타민 D", "햇빛", null, null),
                new FakeAnswer.Item("POTASSIUM", "칼륨", "혈압", null, null),
                new FakeAnswer.Item("IRON", "철분", "피로", null, null)), "요약"));
        Map<String, Object> out = new LinkedHashMap<>();
        for (String version : VERSIONS) {
            RecommendationPipeline pipeline = registry.get(version);
            for (int i = 0; i < WARMUP; i++) {
                pipeline.run(input);
            }
            PerfStats stats = new PerfStats();
            for (int i = 0; i < RUNS; i++) {
                long t0 = System.nanoTime();
                pipeline.run(input);
                stats.add((System.nanoTime() - t0) / 1_000_000.0);
            }
            out.put(version, stats.summary());
            System.out.println("[PERF] " + version + " " + stats.summary());
        }
        String label = System.getProperty("perf.label", "run");
        Path file = Path.of("build", "perf", "pipeline-throughput-" + label + ".json");
        Files.createDirectories(file.getParent());
        ObjectMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        Files.writeString(file, mapper.writeValueAsString(out));
    }
}
