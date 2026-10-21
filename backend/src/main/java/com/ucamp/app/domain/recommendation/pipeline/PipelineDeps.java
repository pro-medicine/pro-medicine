package com.ucamp.app.domain.recommendation.pipeline;

import com.ucamp.app.global.metrics.StageTimer;
import com.ucamp.app.infra.llm.LlmClient;
import com.ucamp.app.infra.llm.LlmProperties;
import com.ucamp.app.infra.llm.log.LlmCallLogService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 구조화 출력 파이프라인(V1~)이 함께 쓰는 협력 객체 묶음. 버전 클래스의 생성자를 짧게 유지하기 위함
@Getter
@Component
@RequiredArgsConstructor
public class PipelineDeps {
    private final LlmClient llmClient;
    private final LlmCallLogService llmCallLogService;
    private final LlmProperties llmProperties;
    private final PromptTemplates promptTemplates;
    private final InputFormatter inputFormatter;
    private final RecommendationOutputValidator validator;
    private final StageTimer stageTimer;
}
