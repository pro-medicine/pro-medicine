package com.ucamp.app.domain.survey.entity;

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

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "survey_questions")
public class SurveyQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Long questionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_type", nullable = false, length = 10)
    private AnswerType answerType;

    @Column(name = "profile_key", length = 50)
    private String profileKey;

    @Column(name = "is_sensitive", nullable = false)
    private Boolean isSensitive;

    @Column(name = "guest_visible", nullable = false)
    private Boolean guestVisible;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Builder
    public SurveyQuestion(Survey survey, String code, String questionText, AnswerType answerType,
                          String profileKey, Boolean isSensitive, Boolean guestVisible, Integer displayOrder) {
        this.survey = survey;
        this.code = code;
        this.questionText = questionText;
        this.answerType = answerType;
        this.profileKey = profileKey;
        this.isSensitive = isSensitive != null ? isSensitive : false;
        this.guestVisible = guestVisible != null ? guestVisible : true;
        this.displayOrder = displayOrder;
    }
}
