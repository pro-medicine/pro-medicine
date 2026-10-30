package com.ucamp.app.domain.calendar.repository;

import com.ucamp.app.domain.calendar.entity.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {
    List<CalendarEvent> findByUserIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
            Long userId, LocalDateTime start, LocalDateTime end);

    Optional<CalendarEvent> findByEventIdAndUserId(Long eventId, Long userId);
}
