package com.ucamp.app.domain.health.dto;

import java.util.List;

public record UserConditionsResponse(
        Long userId,
        List<String> conditions
) {
}
