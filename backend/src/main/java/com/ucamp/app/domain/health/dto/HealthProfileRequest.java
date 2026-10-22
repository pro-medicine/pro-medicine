package com.ucamp.app.domain.health.dto;

import java.math.BigDecimal;

// PUT 요청: 보내지 않은(null) 항목은 빈 값으로 저장됨
public record HealthProfileRequest(
        Integer birthYear,
        String gender,
        BigDecimal height,
        BigDecimal weight,
        String smoking,
        String drinking,
        String caffeine,
        String dietType,
        String sunExposure
) {
}
