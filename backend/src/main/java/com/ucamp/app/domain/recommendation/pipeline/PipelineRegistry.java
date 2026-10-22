package com.ucamp.app.domain.recommendation.pipeline;

import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

// 버전 이름 → 파이프라인. 모든 버전이 빈으로 공존한다
@Component
public class PipelineRegistry {
    private final Map<String, RecommendationPipeline> pipelines;

    public PipelineRegistry(List<RecommendationPipeline> pipelines) {
        this.pipelines = pipelines.stream()
                .collect(Collectors.toMap(RecommendationPipeline::version, Function.identity(), (a, b) -> {
                    throw new IllegalStateException("duplicate pipeline version " + a.version());
                }, TreeMap::new));
    }

    public RecommendationPipeline get(String version) {
        RecommendationPipeline p = pipelines.get(version);
        if (p == null) {
            throw new BusinessException(ErrorCode.RECOMMENDATION_UNKNOWN_PIPELINE);
        }
        return p;
    }
}
