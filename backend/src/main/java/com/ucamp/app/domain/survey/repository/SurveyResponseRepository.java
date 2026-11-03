package com.ucamp.app.domain.survey.repository;

import com.ucamp.app.domain.survey.entity.RespondentType;
import com.ucamp.app.domain.survey.entity.ResponseStatus;
import com.ucamp.app.domain.survey.entity.SurveyResponse;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SurveyResponse r where r.responseId = :responseId")
    Optional<SurveyResponse> findForGuestClaim(@Param("responseId") Long responseId);

    Optional<SurveyResponse> findFirstByUser_IdAndRespondentTypeOrderBySubmittedAtDesc(
            Long userId, RespondentType respondentType);

    @Query("select r from SurveyResponse r where r.user.id = :userId "
            + "and r.respondentType = :respondentType and r.status = :status "
            + "and exists (select a.answerId from SurveyAnswer a "
            + "where a.response = r and a.question.code = :questionCode) "
            + "order by r.submittedAt desc, r.responseId desc")
    List<SurveyResponse> findLatestWithAnswer(@Param("userId") Long userId,
                                              @Param("respondentType") RespondentType respondentType,
                                              @Param("status") ResponseStatus status,
                                              @Param("questionCode") String questionCode,
                                              Pageable pageable);
}
