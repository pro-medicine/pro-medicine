package com.ucamp.app.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucamp.app.domain.health.entity.Drug;
import com.ucamp.app.domain.health.repository.DrugRepository;
import com.ucamp.app.domain.recommendation.pipeline.PipelineRegistry;
import com.ucamp.app.eval.EvalCaseFile.EvalCase;
import com.ucamp.app.infra.llm.FakeLlmClient;
import com.ucamp.app.infra.llm.log.LlmCallLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 API를 쓰기 전에 러너 자체를 검증한다 (Fake LLM)
@SpringBootTest
class EvalRunnerTest {
    @Autowired
    PipelineRegistry pipelineRegistry;
    @Autowired
    DrugRepository drugRepository;
    @Autowired
    FakeLlmClient fake;
    @Autowired
    LlmCallLogRepository logRepository;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    void 평가셋_형식_최소_30케이스와_구성() {
        EvalCaseFile file = EvalRunner.loadCases(objectMapper);

        assertThat(file.cases()).hasSizeGreaterThanOrEqualTo(30);
        assertThat(file.meta().reproCaseIds()).hasSize(10);
        Map<String, Long> groups = file.cases().stream()
                .collect(Collectors.groupingBy(c -> c.tags().get(0), Collectors.counting()));
        assertThat(groups.get("SINGLE")).isGreaterThanOrEqualTo(10);
        assertThat(groups.get("MULTI")).isGreaterThanOrEqualTo(10);
        assertThat(groups.get("NONE")).isGreaterThanOrEqualTo(5);
        assertThat(groups.get("GUEST")).isGreaterThanOrEqualTo(5);
        for (EvalCase c : file.cases()) {
            assertThat(c.rationale()).isNotBlank();
            assertThat(c.expected().mustInclude().stream().filter(c.expected().mustExclude()::contains)).isEmpty();
        }
    }

    @Test
    void 정답을_주는_Fake면_recall_1_violation_0() {
        EvalRunner.Summary s = runner().run(config("V0", EvalRunner.LlmMode.FAKE, "test-oracle"), cases());

        assertThat(s.metrics().recall()).isEqualTo(1.0);
        assertThat(s.metrics().violationRate()).isEqualTo(0.0);
        assertThat(s.metrics().doseErrorRate()).isEqualTo(0.0);
        assertThat(s.metrics().parseFailRate()).isEqualTo(0.0);
        assertThat(s.metrics().precision()).isEqualTo(1.0);
        assertThat(s.llmCalls()).isEqualTo(cases().size());
        assertThat(s.costPerReq()).isNull(); // fake는 비용 없음
    }

    @Test
    void 틀린_답을_주는_Fake면_지표가_나빠진다() {
        // 모든 케이스에 베타카로틴·칼륨만 추천 (흡연자·혈압약·비회원 케이스에서 위반)
        fake.setResponder(req -> fake.textResponse(req,
                "{\"recommendations\":[{\"name\":\"베타카로틴\"},{\"name\":\"칼륨\"}],\"summary\":\"x\"}"));
        EvalRunner r = new EvalRunner(pipelineRegistry, drugs(), fake, logRepository,
                IngredientAliases.load(objectMapper), objectMapper, null, null);
        // ANTHROPIC 모드는 러너가 Fake 응답을 바꾸지 않으므로 위에서 정한 응답이 그대로 쓰인다
        EvalRunner.Summary s = r.run(config("V0", EvalRunner.LlmMode.ANTHROPIC, "test-wrong"), cases());
        fake.reset();

        assertThat(s.metrics().recall()).isEqualTo(0.0);
        long expectedViolations = cases().stream().filter(c -> c.expected().mustExclude().contains("BETA_CAROTENE")
                || c.expected().mustExclude().contains("POTASSIUM")).count();
        assertThat(s.metrics().violationRate()).isEqualTo((double) expectedViolations / cases().size());
        assertThat(s.metrics().precision()).isLessThan(0.2);
    }

    @Test
    void 재현성_모드는_10케이스_5회() {
        EvalCaseFile file = EvalRunner.loadCases(objectMapper);
        List<EvalCase> repro = EvalRunner.select(file, true, 0);

        EvalRunner.Summary s = runner().run(new EvalRunner.Config("V0", true, 0, 5, EvalRunner.LlmMode.FAKE,
                "test-repro"), repro);

        assertThat(s.cases()).isEqualTo(10);
        assertThat(s.details()).hasSize(50);
        assertThat(s.metrics().reproducibilitySet()).isEqualTo(1.0);
        assertThat(s.metrics().reproducibilityDose()).isEqualTo(1.0);
    }

    private List<EvalCase> cases() {
        return EvalRunner.loadCases(objectMapper).cases();
    }

    private EvalRunner runner() {
        return new EvalRunner(pipelineRegistry, drugs(), fake, logRepository, IngredientAliases.load(objectMapper),
                objectMapper, null, null);
    }

    private Map<String, Drug> drugs() {
        return drugRepository.findAll().stream().collect(Collectors.toMap(Drug::getDrugId, Function.identity()));
    }

    private static EvalRunner.Config config(String version, EvalRunner.LlmMode mode, String id) {
        return new EvalRunner.Config(version, false, 0, 1, mode, id + "-" + System.nanoTime());
    }
}
