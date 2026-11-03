package com.ucamp.app.domain.survey.repository;

import com.ucamp.app.domain.survey.entity.RespondentType;
import com.ucamp.app.domain.survey.entity.SurveyResponse;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SurveyResponse r where r.responseId = :responseId")
    Optional<SurveyResponse> findForGuestClaim(@Param("responseId") Long responseId);

    Optional<SurveyResponse> findFirstByUser_IdAndRespondentTypeOrderBySubmittedAtDesc(
            Long userId, RespondentType respondentType);
}
