package com.ucamp.app.domain.calendar.dto;

import com.ucamp.app.domain.calendar.entity.CalendarEvent;
import com.ucamp.app.domain.calendar.enums.EventStatus;
import com.ucamp.app.domain.calendar.enums.EventType;

import java.time.LocalDateTime;

public record CalendarEventResponse(
        Long eventId,
        EventType eventType,
        String title,
        LocalDateTime scheduledAt,
        String notes,
        EventStatus status
) {
    public static CalendarEventResponse from(CalendarEvent event) {
        return new CalendarEventResponse(
                event.getEventId(),
                event.getEventType(),
                event.getTitle(),
                event.getScheduledAt(),
                event.getNotes(),
                event.getStatus()
        );
    }
}
