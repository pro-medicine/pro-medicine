package com.ucamp.app.domain.recommendation.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

// 추천 기능 기본 설정은 config/recommendation-defaults.properties (application.yml보다 우선순위가 낮다)
@Configuration
@EnableConfigurationProperties(RecommendationProperties.class)
@PropertySource(value = "classpath:config/recommendation-defaults.properties", encoding = "UTF-8")
public class RecommendationConfig {
}
