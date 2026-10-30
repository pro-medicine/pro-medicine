package com.ucamp.app.domain.calendar.service;

import com.ucamp.app.domain.calendar.dto.CalendarEventRequest;
import com.ucamp.app.domain.calendar.dto.CalendarEventResponse;
import com.ucamp.app.domain.calendar.entity.CalendarEvent;
import com.ucamp.app.domain.calendar.enums.EventStatus;
import com.ucamp.app.domain.calendar.repository.CalendarEventRepository;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarEventService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long MAX_DAYS = 62;

    private final CalendarEventRepository calendarEventRepository;
    private final UserRepository userRepository;

    public List<CalendarEventResponse> getEvents(Long userId, LocalDate start, LocalDate end) {
        validateRange(start, end);
        requireUser(userId);
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay();
        return calendarEventRepository
                .findByUserIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(userId, from, to)
                .stream()
                .map(CalendarEventResponse::from)
                .toList();
    }

    @Transactional
    public CalendarEventResponse create(Long userId, CalendarEventRequest request) {
        requireUser(userId);
        validateEvent(request);
        CalendarEvent event = CalendarEvent.builder()
                .userId(userId)
                .eventType(request.eventType())
                .title(request.title().trim())
                .scheduledAt(request.scheduledAt())
                .notes(request.notes())
                .status(EventStatus.PLANNED)
                .build();
        return CalendarEventResponse.from(calendarEventRepository.save(event));
    }

    @Transactional
    public CalendarEventResponse update(Long userId, Long eventId, CalendarEventRequest request) {
        validateEvent(request);
        CalendarEvent event = findEvent(userId, eventId);
        event.update(request.eventType(), request.title().trim(), request.scheduledAt(), request.notes(), event.getStatus());
        return CalendarEventResponse.from(event);
    }

    @Transactional
    public CalendarEventResponse changeStatus(Long userId, Long eventId, EventStatus status) {
        CalendarEvent event = findEvent(userId, eventId);
        event.changeStatus(status);
        return CalendarEventResponse.from(event);
    }

    @Transactional
    public void delete(Long userId, Long eventId) {
        CalendarEvent event = findEvent(userId, eventId);
        calendarEventRepository.delete(event);
    }

    private CalendarEvent findEvent(Long userId, Long eventId) {
        return calendarEventRepository
                .findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
    }

    private void validateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null || start.isAfter(end) || ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) throw new BusinessException(ErrorCode.INVALID_INPUT);
    }

    private void validateEvent(CalendarEventRequest request) {
        if (request.scheduledAt() == null
                || request.eventType() == null
                || request.title() == null
                || request.title().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void requireUser(Long userId) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw new BusinessException(
                    userId == null
                            ? ErrorCode.UNAUTHORIZED
                            : ErrorCode.USER_NOT_FOUND
            );
        }
    }
}
