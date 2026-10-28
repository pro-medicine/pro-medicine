package com.ucamp.app.domain.recommendation.entity;

import com.ucamp.app.domain.personalized.entity.AiQuestionSession;
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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
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

    // V1(설문 기반) 추천. V2 정밀 추천은 null이고 session이 있다 (STEP 14)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "response_id")
    private SurveyResponse response;

    // STEP 14 추가: V2 맞춤 질문 회차. 회차가 90일 뒤 삭제되면 null (추천 결과는 남는다)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private AiQuestionSession session;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "model_name", length = MODEL_NAME_LENGTH)
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

    @Column(name = "error_code", length = ERROR_CODE_LENGTH)
    private String errorCode;

    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";
    public static final int MODEL_NAME_LENGTH = 60;
    public static final int ERROR_CODE_LENGTH = 30;

    @Builder
    public AiRecommendation(SurveyResponse response, AiQuestionSession session, String summary, String modelName,
                            String status) {
        // 설문 응답(V1) 또는 맞춤 질문 회차(V2) 중 하나는 있어야 한다. DB CHECK는 90일 삭제(session SET NULL) 때문에 걸지 않는다
        if (response == null && session == null) {
            throw new IllegalArgumentException("response 또는 session이 필요합니다");
        }
        this.response = response;
        this.session = session;
        this.summary = summary;
        this.modelName = modelName;
        this.status = status != null ? status : DONE;
        if (DONE.equals(this.status)) {
            this.finishedAt = LocalDateTime.now();
        }
    }

    /** V1 설문 응답 ID. V2 정밀 추천이면 null */
    public Long responseIdOrNull() {
        return response == null ? null : response.getResponseId();
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
}
