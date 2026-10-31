package com.ucamp.app.infra.llm.guard;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * LLM 기능 차단 목록. blocked_until이 NULL이면 영구 차단.
 */
@Entity
@Table(name = "llm_blocklist")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LlmBlocklist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 100)
    private String reason;

    /** NULL이면 영구 차단. 값이 있으면 해당 시각까지 일시 정지 */
    private LocalDateTime blockedUntil;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static LlmBlocklist suspend(Long userId, String reason, LocalDateTime until) {
        LlmBlocklist b = new LlmBlocklist();
        b.userId = userId;
        b.reason = reason;
        b.blockedUntil = until;
        return b;
    }

    public static LlmBlocklist permanentBlock(Long userId, String reason) {
        LlmBlocklist b = new LlmBlocklist();
        b.userId = userId;
        b.reason = reason;
        b.blockedUntil = null;
        return b;
    }

    /** 현재 활성(영구 또는 아직 만료 안 됨)인지 */
    public boolean isActive() {
        return blockedUntil == null || blockedUntil.isAfter(LocalDateTime.now());
    }

    /** 영구 차단인지 */
    public boolean isPermanent() {
        return blockedUntil == null;
    }

    /** 남은 정지 시간 (초). 영구면 -1 */
    public long remainingSeconds() {
        if (blockedUntil == null) return -1;
        long secs = java.time.Duration.between(LocalDateTime.now(), blockedUntil).getSeconds();
        return Math.max(secs, 0);
    }
}
