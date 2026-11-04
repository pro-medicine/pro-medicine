package com.ucamp.app.domain.medicine.dto;

import java.time.LocalDate;

public record DatedInteractionWarning(
        LocalDate date,
        InteractionWarning warning
) {
}
