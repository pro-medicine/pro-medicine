package com.ucamp.app.domain.health.dto;

import java.math.BigDecimal;

// health_profiles 컬럼 하나에 반영할 값. 선택형은 valueCode, 숫자형은 valueNumber
public record ProfileValue(String valueCode, BigDecimal valueNumber) {
}
