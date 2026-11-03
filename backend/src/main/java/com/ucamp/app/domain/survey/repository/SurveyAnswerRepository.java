package com.ucamp.app.domain.survey.repository;

import com.ucamp.app.domain.survey.entity.SurveyAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface SurveyAnswerRepository extends JpaRepository<SurveyAnswer, Long> {
    @Query("select a from SurveyAnswer a join fetch a.question left join fetch a.option "
            + "where a.response.responseId = :responseId order by a.answerId")
    List<SurveyAnswer> findDetailByResponseId(@Param("responseId") Long responseId);

    long countByResponse_ResponseId(Long responseId);

    // 회원이 이 문항(Q-CONDITIONS 등)에 한 번이라도 답했는지 ("없음" 선택 포함). V2 모드 판단용
    boolean existsByResponse_User_IdAndQuestion_Code(Long userId, String questionCode);

    // V7.1 추천 기록의 "불편한 점": 응답들의 한 문항(Q-GOAL) 선택지 라벨 [responseId, label] (응답 안 선택 순서)
    @Query("select a.response.responseId, o.label from SurveyAnswer a join a.question q join a.option o "
            + "where q.code = :questionCode and a.response.responseId in :responseIds order by a.answerId")
    List<Object[]> findOptionLabels(@Param("questionCode") String questionCode,
                                    @Param("responseIds") Collection<Long> responseIds);

    // V7.1 90일 보관: 제출한 지 cutoff보다 오래된 응답의 한 문항(Q-GOAL) 답만 삭제. 응답·다른 답은 남긴다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SurveyAnswer a where a.question.questionId in "
            + "(select q.questionId from SurveyQuestion q where q.code = :questionCode) "
            + "and a.response.responseId in "
            + "(select r.responseId from SurveyResponse r where r.submittedAt < :cutoff)")
    int deleteAnswersSubmittedBefore(@Param("questionCode") String questionCode, @Param("cutoff") LocalDateTime cutoff);
}
