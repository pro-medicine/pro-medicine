package com.ucamp.app.eval;

import com.ucamp.app.domain.recommendation.pipeline.RecommendationInput;
import org.springframework.context.ApplicationContext;

import java.util.function.Function;

/**
 * 버전에 따라 평가에 붙는 부가 기능. 해당 기능이 아직 없는 STEP에서는 null을 돌려준다.
 * - overLimitChecker: 기준표 상한으로 over_limit_rate 계산 (STEP 06부터)
 * - routedModel: V8 모델 라우팅 결과별 recall 분리 (STEP 08부터)
 */
final class EvalHooks {
    private EvalHooks() {
    }

    static EvalRunner.OverLimitChecker overLimitChecker(ApplicationContext context) {
        return null;
    }

    static Function<RecommendationInput, String> routedModel(ApplicationContext context, String version) {
        return null;
    }
}
