package com.ucamp.app.domain.survey.entity;

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

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "question_options")
public class QuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "option_id")
    private Long optionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private SurveyQuestion question;

    @Column(name = "value_code", nullable = false, length = 50)
    private String valueCode;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Builder
    public QuestionOption(SurveyQuestion question, String valueCode, String label, Integer displayOrder) {
        this.question = question;
        this.valueCode = valueCode;
        this.label = label;
        this.displayOrder = displayOrder;
    }
}
