package com.ucamp.app.domain.calendar.entity;

import com.ucamp.app.domain.calendar.enums.EventStatus;
import com.ucamp.app.domain.calendar.enums.EventType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "calendar_event")
@EntityListeners(AuditingEntityListener.class)
public class CalendarEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private CalendarEvent(
            Long userId,
            EventType eventType,
            String title,
            LocalDateTime scheduledAt,
            LocalDateTime endAt,
            String notes,
            EventStatus status
    ) {
        this.userId = userId;
        this.eventType = eventType;
        this.title = title;
        this.scheduledAt = scheduledAt;
        this.endAt = endAt;
        this.notes = notes;
        this.status = status != null ? status : EventStatus.PLANNED;
    }

    public void update(
            EventType eventType,
            String title,
            LocalDateTime scheduledAt,
            LocalDateTime endAt,
            String notes,
            EventStatus status
    ) {
        this.eventType = eventType;
        this.title = title;
        this.scheduledAt = scheduledAt;
        this.endAt = endAt;
        this.notes = notes;
        this.status = status;
    }

    public void changeStatus(EventStatus status) {
        this.status = status;
    }
}
