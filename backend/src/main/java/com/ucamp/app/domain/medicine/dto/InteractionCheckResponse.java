package com.ucamp.app.domain.medicine.dto;

import java.util.List;

public record InteractionCheckResponse(
        boolean checked,
        String medicineName,
        int unverifiedSupplementCount,
        List<InteractionWarning> warnings
) {
    public static InteractionCheckResponse unchecked(String name) {
        return new InteractionCheckResponse(false, name, 0, List.of());
    }
}
