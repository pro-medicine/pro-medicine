package com.ucamp.app.domain.health.dto;

import java.util.List;

// PUT 요청: 질환 목록 전체를 교체, 빈 배열이면 "없음"
public record UserConditionsRequest(
        List<String> conditions
) {
}
