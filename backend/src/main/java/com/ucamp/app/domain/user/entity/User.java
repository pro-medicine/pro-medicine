package com.ucamp.app.domain.user.entity;

import com.ucamp.app.global.common.Timestamped;
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

// users 테이블에는 created_at, updated_at만 있어서 Timestamped를 상속하지 않음
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class User extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Column(nullable = false)
    private boolean shareIntakeStatus;

    @Column(name = "sensitive_consent", nullable = false)
    private boolean sensitiveConsent;

    @Column(name = "sensitive_consent_at")
    private LocalDateTime sensitiveConsentAt;

    @Column(name = "last_active_at", nullable = false)
    private LocalDateTime lastActiveAt = LocalDateTime.now();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private User(String email, String passwordHash, String nickname) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.shareIntakeStatus = false;
        this.sensitiveConsent = false;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void changeShareIntakeStatus(boolean shareIntakeStatus) {
        this.shareIntakeStatus = shareIntakeStatus;
    }

    // 민감정보(건강 설문) 수집 동의. 이미 동의했으면 처음 동의 시각을 유지
    public void agreeSensitiveConsent() {
        if (this.sensitiveConsent) {
            return;
        }
        this.sensitiveConsent = true;
        this.sensitiveConsentAt = LocalDateTime.now();
    }

    // 마지막 접속일 체크용
    public void touchActive() {
        this.lastActiveAt = LocalDateTime.now();
    }
}
