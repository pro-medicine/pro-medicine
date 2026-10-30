package com.ucamp.app.domain.calendar.controller;

import com.ucamp.app.domain.calendar.dto.CalendarEventRequest;
import com.ucamp.app.domain.calendar.dto.CalendarEventResponse;
import com.ucamp.app.domain.calendar.dto.CalendarEventStatusRequest;
import com.ucamp.app.domain.calendar.service.CalendarEventService;
import com.ucamp.app.global.auth.CurrentUserId;
import com.ucamp.app.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/calendar-events")
public class CalendarEventController {

    private final CalendarEventService calendarEventService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CalendarEventResponse>>> getEvents(
            @CurrentUserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        List<CalendarEventResponse> responses = calendarEventService.getEvents(userId, start, end);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(responses));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CalendarEventResponse>> create(
            @CurrentUserId Long userId,
            @Valid @RequestBody CalendarEventRequest request) {
        CalendarEventResponse response = calendarEventService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> update(
            @CurrentUserId Long userId,
            @PathVariable Long eventId,
            @Valid @RequestBody CalendarEventRequest request) {
        CalendarEventResponse response = calendarEventService.update(userId, eventId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{eventId}/status")
    public ResponseEntity<ApiResponse<CalendarEventResponse>> changeStatus(
            @CurrentUserId Long userId,
            @PathVariable Long eventId,
            @Valid @RequestBody CalendarEventStatusRequest request) {
        CalendarEventResponse response = calendarEventService.changeStatus(userId, eventId, request.status());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> delete(@CurrentUserId Long userId, @PathVariable Long eventId) {
        calendarEventService.delete(userId, eventId);
        return ResponseEntity.noContent().build();
    }
}
