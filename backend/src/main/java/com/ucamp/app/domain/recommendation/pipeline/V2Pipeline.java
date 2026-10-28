package com.ucamp.app.domain.recommendation.pipeline;

import org.springframework.stereotype.Component;

/**
 * V2: 용량 계산 분리. V1 대비 바뀐 점
 * - 스키마·프롬프트에서 amount·unit 제거 ("용량은 쓰지 마라")
 * - 용량 = 기준표 권장량 − 현재 섭취량(설문 Q-SUPPLEMENTS), 상한 초과 시 상한까지 (DoseCalculator, calc 단계)
 */
@Component
public class V2Pipeline extends StructuredPipeline {
    public V2Pipeline(PipelineDeps deps) {
        super(deps, new Options("V2", Prompt.V2, Feature.DOSE_CALC));
    }
}
