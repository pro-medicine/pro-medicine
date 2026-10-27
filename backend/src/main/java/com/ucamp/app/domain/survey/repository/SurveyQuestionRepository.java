package com.ucamp.app.domain.survey.repository;

import com.ucamp.app.domain.survey.entity.SurveyQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SurveyQuestionRepository extends JpaRepository<SurveyQuestion, Long> {
    List<SurveyQuestion> findBySurvey_SurveyIdOrderByDisplayOrderAsc(Long surveyId);
}
