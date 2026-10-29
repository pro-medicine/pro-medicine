package com.ucamp.app.domain.review.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ReviewProductProperties.class)
public class ReviewProductConfig {
}
