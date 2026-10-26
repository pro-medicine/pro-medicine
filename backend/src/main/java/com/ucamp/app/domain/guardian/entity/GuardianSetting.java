package com.ucamp.app.domain.guardian.entity;

import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.global.common.Timestamped;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "guardian_settings",
    uniqueConstraints = @UniqueConstraint(
            name = "uq_guardian_pair",
            columnNames = {"user_id", "guardian_user_id"}
    ))
public class GuardianSetting extends Timestamped {

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

    public static GuardianSetting request(User user, User guardianUser, int inactivityDays) {
        if (user.getId().equals(guardianUser.getId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        validateDays(inactivityDays);
        return GuardianSetting.builder()
                .user(user)
                .guardianUser(guardianUser)
                .inactivityDays(inactivityDays)
                .build();
    }

    public void accept() {
        this.consentedAt = LocalDateTime.now();
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void markNotified() {
        this.lastNotifiedAt = LocalDateTime.now();
    }

    public void changeInactivityDays(int days) {
        validateDays(days);
        this.inactivityDays = days;
    }

    private static void validateDays(int days) {
        if (days < 1 || days > 30) throw new BusinessException(ErrorCode.INVALID_INACTIVITY_DAYS);
    }
}
