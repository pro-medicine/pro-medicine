package com.ucamp.app.domain.survey.repository;

import com.ucamp.app.domain.survey.entity.SurveyAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SurveyAnswerRepository extends JpaRepository<SurveyAnswer, Long> {
    @Query("select a from SurveyAnswer a join fetch a.question left join fetch a.option "
            + "where a.response.responseId = :responseId order by a.answerId")
    List<SurveyAnswer> findDetailByResponseId(@Param("responseId") Long responseId);

    long countByResponse_ResponseId(Long responseId);

    // 회원이 이 문항(Q-CONDITIONS 등)에 한 번이라도 답했는지 ("없음" 선택 포함). V2 모드 판단용
    boolean existsByResponse_User_IdAndQuestion_Code(Long userId, String questionCode);
}
