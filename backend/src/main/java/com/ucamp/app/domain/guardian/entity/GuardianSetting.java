package com.ucamp.app.domain.guardian.entity;

import com.ucamp.app.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "guardian_settings")
public class GuardianSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guardian_setting_id")
    private Long guardianSettingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guardian_user_id", nullable = false)
    private User guardianUser;

    @Column(name = "inactivity_days", nullable = false)
    private Integer inactivityDays;

    @Column(name = "consented_at")
    private LocalDateTime consentedAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "last_notified_at")
    private LocalDateTime lastNotifiedAt;

    @Builder
    public GuardianSetting(User user, User guardianUser, Integer inactivityDays, LocalDateTime consentedAt,
                           Boolean isActive, LocalDateTime lastNotifiedAt) {
        this.user = user;
        this.guardianUser = guardianUser;
        this.inactivityDays = inactivityDays != null ? inactivityDays : 3;
        this.consentedAt = consentedAt;
        this.isActive = isActive != null ? isActive : false;
        this.lastNotifiedAt = lastNotifiedAt;
    }
}
