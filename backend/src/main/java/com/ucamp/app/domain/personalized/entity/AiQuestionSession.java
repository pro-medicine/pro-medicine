package com.ucamp.app.domain.personalized.entity;

import com.ucamp.app.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * V2 맞춤 질문 회차 (STEP 14). 회원 한 명이 맞춤 질문을 시작해서 정밀 추천까지 가는 한 번의 흐름.
 * 맞춤 답변은 프로필에 저장하지 않고 이 회차(와 ai_question_turns)에만 남긴다. 90일 뒤 삭제한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "ai_question_sessions", indexes = {
        @Index(name = "idx_pq_sessions_user", columnList = "user_id, started_at"),
        @Index(name = "idx_pq_sessions_started", columnList = "started_at")})
@EntityListeners(AuditingEntityListener.class)
public class AiQuestionSession {

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String READY = "READY";
    public static final String DONE = "DONE";
    public static final String ABANDONED = "ABANDONED";
    public static final String FAILED = "FAILED";
    public static final int ERROR_CODE_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "status", nullable = false, length = 15)
    private String status;

    // NOT_APPLICABLE / POSSIBLE / PREGNANT / LACTATING (19~50세 여성만, 고정 질문 답)
    @Column(name = "pregnancy", length = 20)
    private String pregnancy;

    // LLM 질문 수 (고정 질문 제외)
    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "stopped_by_user", nullable = false)
    private boolean stoppedByUser;

    @Column(name = "recommendation_id")
    private Long recommendationId;

    // 모니터링용: 비교한 V1 추천과 V1 대비 변화 (사용자에게 보이지 않음)
    @Column(name = "baseline_recommendation_id")
    private Long baselineRecommendationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diff_json")
    private String diffJson;

    @Column(name = "error_code", length = ERROR_CODE_LENGTH)
    private String errorCode;

    @CreatedDate
    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public static AiQuestionSession start(User user) {
        AiQuestionSession session = new AiQuestionSession();
        session.user = user;
        session.status = IN_PROGRESS;
        return session;
    }

    public boolean isInProgress() {
        return IN_PROGRESS.equals(status);
    }

    public boolean isReady() {
        return READY.equals(status);
    }

    public boolean isDone() {
        return DONE.equals(status);
    }

    public void answerPregnancy(String pregnancy) {
        this.pregnancy = pregnancy;
    }

    public void countQuestion() {
        this.questionCount++;
    }

    public void markReady(boolean stoppedByUser) {
        this.status = READY;
        this.stoppedByUser = stoppedByUser;
    }

    public void markDone(Long recommendationId, Long baselineRecommendationId, String diffJson) {
        this.status = DONE;
        this.recommendationId = recommendationId;
        this.baselineRecommendationId = baselineRecommendationId;
        this.diffJson = diffJson;
        this.finishedAt = LocalDateTime.now();
    }

    public void markAbandoned() {
        this.status = ABANDONED;
        this.finishedAt = LocalDateTime.now();
    }

    public void markFailed(String errorCode) {
        this.status = FAILED;
        this.errorCode = errorCode == null || errorCode.length() <= ERROR_CODE_LENGTH
                ? errorCode : errorCode.substring(0, ERROR_CODE_LENGTH);
        this.finishedAt = LocalDateTime.now();
    }
}
