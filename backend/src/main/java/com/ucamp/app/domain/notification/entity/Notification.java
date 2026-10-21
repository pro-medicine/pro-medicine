package com.ucamp.app.domain.notification.entity;

import com.ucamp.app.domain.intake.entity.IntakeSchedule;
import com.ucamp.app.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    private IntakeSchedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actorUser;

    // TODO: enum 전환
    @Column(name = "notification_type", nullable = false, length = 30)
    private String notificationType;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "dedup_key", length = 150)
    private String dedupKey;

    @Builder
    public Notification(User user, IntakeSchedule schedule, User actorUser, String notificationType, String title,
                        LocalDateTime scheduledAt, LocalDateTime sentAt, LocalDateTime readAt, String dedupKey) {
        this.user = user;
        this.schedule = schedule;
        this.actorUser = actorUser;
        this.notificationType = notificationType;
        this.title = title;
        this.scheduledAt = scheduledAt;
        this.sentAt = sentAt;
        this.readAt = readAt;
        this.dedupKey = dedupKey;
    }
}
