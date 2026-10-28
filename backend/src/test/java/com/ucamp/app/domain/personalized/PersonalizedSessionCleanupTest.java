package com.ucamp.app.domain.personalized;

import com.ucamp.app.domain.personalized.entity.AiQuestionSession;
import com.ucamp.app.domain.personalized.entity.AiQuestionTurn;
import com.ucamp.app.domain.personalized.repository.AiQuestionSessionRepository;
import com.ucamp.app.domain.personalized.repository.AiQuestionTurnRepository;
import com.ucamp.app.domain.personalized.service.PersonalizedSessionCleanupScheduler;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.support.TestUsers;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 설계 8-4: 90일 지난 회차 삭제 (질문은 CASCADE, 추천 결과는 남고 session_id만 NULL)
@SpringBootTest
@Transactional
class PersonalizedSessionCleanupTest {
    @Autowired
    PersonalizedSessionCleanupScheduler scheduler;
    @Autowired
    AiQuestionSessionRepository sessionRepository;
    @Autowired
    AiQuestionTurnRepository turnRepository;
    @Autowired
    AiRecommendationRepository recommendationRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    MeterRegistry meterRegistry;

    @Test
    void 시작한_지_90일_지난_회차만_삭제한다() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 26, 4, 0);
        User user = TestUsers.create(userRepository, true);
        AiQuestionSession old = session(user, now.minusDays(91));
        AiQuestionSession boundary = session(user, now.minusDays(90).plusMinutes(1));
        AiQuestionSession recent = session(user, now.minusDays(1));
        turnRepository.saveAndFlush(AiQuestionTurn.builder().session(old).turnNo(1).topic("CONCERN_OPEN").depth(1)
                .questionText("q").answerType("MULTI").optionsJson("[]").build());
        AiRecommendation rec = recommendationRepository.saveAndFlush(AiRecommendation.builder()
                .session(old).summary("정밀 추천").build());
        double before = meterRegistry.counter("pq.session.purged").count();

        int deleted = scheduler.purge(now);

        assertThat(deleted).isEqualTo(1);
        assertThat(count("select count(*) from ai_question_sessions where session_id = ?", old.getSessionId())).isZero();
        assertThat(count("select count(*) from ai_question_sessions where session_id in (?, ?)",
                boundary.getSessionId(), recent.getSessionId())).isEqualTo(2);
        assertThat(count("select count(*) from ai_question_turns where session_id = ?", old.getSessionId())).isZero();
        assertThat(count("select count(*) from ai_recommendations where recommendation_id = ? and session_id is null",
                rec.getRecommendationId())).isEqualTo(1);
        assertThat(meterRegistry.counter("pq.session.purged").count() - before).isEqualTo(1);
    }

    private AiQuestionSession session(User user, LocalDateTime startedAt) {
        AiQuestionSession s = sessionRepository.saveAndFlush(AiQuestionSession.start(user));
        jdbcTemplate.update("update ai_question_sessions set started_at = ? where session_id = ?",
                Timestamp.valueOf(startedAt), s.getSessionId());
        return s;
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }
}
