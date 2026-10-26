package com.ucamp.app.domain.personalized;

import com.ucamp.app.domain.personalized.entity.AiQuestionSession;
import com.ucamp.app.domain.personalized.entity.AiQuestionTurn;
import com.ucamp.app.domain.personalized.repository.AiQuestionSessionRepository;
import com.ucamp.app.domain.personalized.repository.AiQuestionTurnRepository;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationRepository;
import com.ucamp.app.domain.recommendation.service.RecommendationStore;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// STEP 14: 맞춤 질문 회차 테이블, ai_recommendations.session_id(response_id NULL 허용)
@SpringBootTest
@Transactional
class PersonalizedSchemaTest {
    @Autowired
    UserRepository userRepository;
    @Autowired
    AiQuestionSessionRepository sessionRepository;
    @Autowired
    AiQuestionTurnRepository turnRepository;
    @Autowired
    AiRecommendationRepository recommendationRepository;
    @Autowired
    RecommendationStore store;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 회차와_질문_JSON을_저장하고_그대로_읽는다() {
        AiQuestionSession session = sessionRepository.saveAndFlush(AiQuestionSession.start(member()));
        AiQuestionTurn turn = turnRepository.saveAndFlush(AiQuestionTurn.builder()
                .session(session).turnNo(1).topic("CONCERN_OPEN").depth(1).questionText("요즘 불편한 점이 있으세요? (최대 2개)")
                .answerType(AiQuestionTurn.MULTI)
                .optionsJson("[{\"label\":\"쉽게 피곤해요\",\"kind\":\"CONCERN\"},{\"label\":\"잘 모르겠어요\",\"kind\":\"UNKNOWN\"}]")
                .build());
        turn.answer("[0]");
        turnRepository.flush();

        String options = jdbcTemplate.queryForObject(
                "select options_json from ai_question_turns where turn_id = ?", String.class, turn.getTurnId());
        assertThat(options).contains("쉽게 피곤해요").doesNotStartWith("\"");
        assertThat(turnRepository.findFirstBySession_SessionIdOrderByTurnNoDesc(session.getSessionId()))
                .get().extracting(AiQuestionTurn::getSelectedJson).isEqualTo("[0]");
        assertThat(session.getStartedAt()).isNotNull();
        assertThat(session.getStatus()).isEqualTo(AiQuestionSession.IN_PROGRESS);
    }

    @Test
    void response_없이_session만으로_추천을_저장하고_조회한다() {
        AiQuestionSession session = sessionRepository.saveAndFlush(AiQuestionSession.start(member()));
        AiRecommendation rec = recommendationRepository.saveAndFlush(AiRecommendation.builder()
                .session(session).summary("정밀 추천").modelName("m").build());

        var response = store.get(rec.getRecommendationId());

        assertThat(response.responseId()).isNull();
        assertThat(response.status()).isEqualTo(AiRecommendation.DONE);
        Long sessionId = jdbcTemplate.queryForObject(
                "select session_id from ai_recommendations where recommendation_id = ?", Long.class,
                rec.getRecommendationId());
        assertThat(sessionId).isEqualTo(session.getSessionId());
    }

    @Test
    void response와_session이_모두_없으면_만들_수_없다() {
        assertThatThrownBy(() -> AiRecommendation.builder().summary("x").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 회차를_지우면_질문은_함께_지워지고_추천은_남아_session_id만_비워진다() {
        AiQuestionSession session = sessionRepository.saveAndFlush(AiQuestionSession.start(member()));
        turnRepository.saveAndFlush(AiQuestionTurn.builder().session(session).turnNo(1).topic("CONCERN_OPEN")
                .depth(1).questionText("q").answerType(AiQuestionTurn.SINGLE).optionsJson("[]").build());
        AiRecommendation rec = recommendationRepository.saveAndFlush(AiRecommendation.builder()
                .session(session).summary("정밀 추천").build());

        int deleted = sessionRepository.deleteStartedBefore(LocalDateTime.now().plusMinutes(1));

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from ai_question_turns where session_id = ?",
                Integer.class, session.getSessionId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select session_id from ai_recommendations where recommendation_id = ?", Long.class,
                rec.getRecommendationId())).isNull();
    }

    private User member() {
        return TestUsers.create(userRepository, true);
    }
}
