package com.ucamp.app.domain.survey.entity;

import com.ucamp.app.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "survey_responses")
public class SurveyResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "response_id")
    private Long responseId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "respondent_type", nullable = false, length = 10)
    private RespondentType respondentType;

    @Column(name = "includes_sensitive", nullable = false)
    private Boolean includesSensitive;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private ResponseStatus status;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Builder
    public SurveyResponse(Survey survey, User user, RespondentType respondentType, Boolean includesSensitive,
                          ResponseStatus status, LocalDateTime submittedAt) {
        this.survey = survey;
        this.user = user;
        this.respondentType = respondentType;
        this.includesSensitive = includesSensitive != null ? includesSensitive : false;
        this.status = status != null ? status : ResponseStatus.IN_PROGRESS;
        this.submittedAt = submittedAt;
    }
}
