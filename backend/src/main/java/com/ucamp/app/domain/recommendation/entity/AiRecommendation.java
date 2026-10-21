package com.ucamp.app.domain.recommendation.entity;

import com.ucamp.app.domain.survey.entity.SurveyResponse;
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
@Table(name = "ai_recommendations")
@EntityListeners(AuditingEntityListener.class)
public class AiRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_id")
    private Long recommendationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "response_id", nullable = false)
    private SurveyResponse response;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "model_name", length = 60)
    private String modelName;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // STEP 09 추가: 비동기 처리 상태. 동기 API로 만든 결과는 DONE. 기존 행이 있어도 추가되도록 DB 기본값을 둔다
    @Column(name = "status", nullable = false, length = 10, columnDefinition = "VARCHAR(10) NOT NULL DEFAULT 'DONE'")
    private String status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "error_code", length = 30)
    private String errorCode;

    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";

    @Builder
    public AiRecommendation(SurveyResponse response, String summary, String modelName, String status) {
        this.response = response;
        this.summary = summary;
        this.modelName = modelName;
        this.status = status != null ? status : DONE;
        if (DONE.equals(this.status)) {
            this.finishedAt = LocalDateTime.now();
        }
    }

    public void markProcessing() {
        this.status = PROCESSING;
        this.startedAt = LocalDateTime.now();
    }

    public void markDone(String summary, String modelName) {
        this.status = DONE;
        this.summary = summary;
        this.modelName = modelName;
        this.finishedAt = LocalDateTime.now();
        this.errorCode = null;
    }

    public void markFailed(String errorCode) {
        this.status = FAILED;
        this.errorCode = errorCode;
        this.finishedAt = LocalDateTime.now();
    }

    public boolean isActiveOrDone() {
        return !FAILED.equals(status);
    }
}
