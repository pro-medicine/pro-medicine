package com.ucamp.app.domain.recommendation.pipeline;

import org.springframework.stereotype.Component;

/**
 * V8 = V7 + 모델 라우팅. 입력 복잡도로 작은 모델 / 기본 모델을 고른다 (ModelRouter).
 * 라우팅 결과는 llm_call_logs.model로 확인할 수 있다.
 */
@Component
public class V8Pipeline extends V7Pipeline {
    public static final String VERSION = "V8";

    private final ModelRouter modelRouter;

    public V8Pipeline(PipelineDeps deps, ModelRouter modelRouter) {
        super(deps, new Options(VERSION, "v4", "v4.1", false));
        this.modelRouter = modelRouter;
    }

    @Override
    protected String chooseModel(RecommendationInput input) {
        return modelRouter.route(input);
    }
}
