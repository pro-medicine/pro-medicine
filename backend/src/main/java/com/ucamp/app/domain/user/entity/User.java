package com.ucamp.app.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Column(name = "share_intake_status", nullable = false)
    private Boolean shareIntakeStatus;

    @Column(name = "sensitive_consent", nullable = false)
    private Boolean sensitiveConsent;

    @Column(name = "sensitive_consent_at")
    private LocalDateTime sensitiveConsentAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public User(String email, String passwordHash, String nickname, Boolean shareIntakeStatus,
                Boolean sensitiveConsent, LocalDateTime sensitiveConsentAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.shareIntakeStatus = shareIntakeStatus != null ? shareIntakeStatus : false;
        this.sensitiveConsent = sensitiveConsent != null ? sensitiveConsent : false;
        this.sensitiveConsentAt = sensitiveConsentAt;
    }
}
