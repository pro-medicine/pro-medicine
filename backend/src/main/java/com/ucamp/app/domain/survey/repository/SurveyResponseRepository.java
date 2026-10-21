package com.ucamp.app.domain.survey.repository;

import com.ucamp.app.domain.survey.entity.SurveyResponse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {
}
