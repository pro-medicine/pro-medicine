package com.ucamp.app.domain.calendar.dto;

import com.ucamp.app.domain.calendar.enums.EventStatus;
import jakarta.validation.constraints.NotNull;

public record CalendarEventStatusRequest(
        @NotNull EventStatus status
        ) {
}
