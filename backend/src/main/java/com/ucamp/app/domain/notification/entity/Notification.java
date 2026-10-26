package com.ucamp.app.domain.notification.entity;

import com.ucamp.app.domain.intake.entity.IntakeSchedule;
import com.ucamp.app.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

// ERD 기준 엔티티(#11)에 알림 기능 로직을 합친 버전
// - notificationType: String → NotificationType enum
// - dedupKey: NOT NULL + UNIQUE (schema.sql과 동일)
// - isRead(), markRead() 추가
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

    // 받는 사람
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    private IntakeSchedule schedule;

    // 알림을 일으킨 사람 (완료한 친구, 팔로우한 사람)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actorUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private NotificationType notificationType;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    // NULL이면 안 읽음
    @Column(name = "read_at")
    private LocalDateTime readAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "dedup_key", nullable = false, unique = true, length = 150)
    private String dedupKey;

    @Builder
    public Notification(User user, IntakeSchedule schedule, User actorUser, NotificationType notificationType,
                        String title, LocalDateTime scheduledAt, LocalDateTime sentAt, String dedupKey) {
        this.user = user;
        this.schedule = schedule;
        this.actorUser = actorUser;
        this.notificationType = notificationType;
        this.title = title;
        this.scheduledAt = scheduledAt;
        this.sentAt = sentAt;
        this.dedupKey = dedupKey;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markRead() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }
}
