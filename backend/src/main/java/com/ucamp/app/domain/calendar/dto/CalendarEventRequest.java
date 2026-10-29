package com.ucamp.app.domain.calendar.dto;

import com.ucamp.app.domain.calendar.enums.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CalendarEventRequest(
        @NotNull EventType eventType,
        @NotBlank @Size(max = 150) String title,
        @NotNull LocalDateTime scheduledAt,
        LocalDateTime endAt,
        @Size(max = 1000) String notes
        ) {
}
