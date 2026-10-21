package com.ucamp.app.domain.recommendation.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

// recommendation.* 설정
@Getter
@Setter
@ConfigurationProperties(prefix = "recommendation")
public class RecommendationProperties {
    /** API가 쓸 파이프라인 버전 (V0, V1 …). 평가 러너는 인자로 따로 고른다 */
    private String pipelineVersion = "V0";
}
