package com.ucamp.app.domain.survey.repository;

import com.ucamp.app.domain.survey.entity.Survey;
import com.ucamp.app.domain.survey.entity.SurveyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SurveyRepository extends JpaRepository<Survey, Long> {
    Optional<Survey> findFirstByStatusOrderBySurveyIdDesc(SurveyStatus status);
}
