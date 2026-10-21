package com.ucamp.app.domain.recommendation.pipeline;

import org.springframework.stereotype.Component;

/**
 * V1: 출력 형식 강제. V0 대비 바뀐 점
 * - 프롬프트의 "JSON으로 답해라" → submit_recommendation 도구(strict JSON Schema, 성분 코드 enum)
 * - 파싱 실패 시 빈 결과 → 검증 실패 이유를 붙여 1회 재요청
 * - 성분 이름 자유 텍스트 → 성분 코드. 용량(amount·unit)은 아직 LLM이 채운다
 */
@Component
public class V1Pipeline extends StructuredPipeline {
    public static final String VERSION = "V1";

    public V1Pipeline(PipelineDeps deps) {
        super(deps, new Options(VERSION, "v1", "v1.1", true));
    }
}
