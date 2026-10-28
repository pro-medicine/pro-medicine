package com.ucamp.app.domain.survey.repository;

import com.ucamp.app.domain.survey.entity.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {
    // 설문 전체 선택지를 한 번에 읽는다 (문항마다 조회하던 N+1 제거, STEP 02 측정)
    List<QuestionOption> findByQuestion_Survey_SurveyIdOrderByDisplayOrderAsc(Long surveyId);
}
