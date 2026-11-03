package com.ucamp.app.domain.medicine.dto;

import java.util.List;

public record InteractionWarning(
        String severity,
        String supplementCode,
        List<String> supplementProducts,
        String message,
        String sourceId
) {
}
