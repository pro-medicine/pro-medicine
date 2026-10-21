package com.ucamp.app.domain.recommendation;

import com.ucamp.app.domain.recommendation.dto.LatestRecommendationResponse;
import com.ucamp.app.domain.recommendation.perf.LoggingNotificationSender;
import com.ucamp.app.domain.recommendation.perf.NotificationSpikeSimulator;
import com.ucamp.app.domain.recommendation.perf.NotificationSpikeSimulator.PlannedNotification;
import com.ucamp.app.domain.recommendation.queue.InMemoryRecommendationQueue;
import com.ucamp.app.domain.recommendation.queue.RecommendationQueue;
import com.ucamp.app.domain.recommendation.queue.RedisRecommendationQueue;
import com.ucamp.app.domain.recommendation.service.RecommendationReuseService;
import com.ucamp.app.domain.recommendation.service.RecommendationService;
import com.ucamp.app.domain.survey.service.SurveySubmitService;
import com.ucamp.app.domain.survey.dto.SubmitSurveyRequest;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.domain.user.repository.UserRepository;
import com.ucamp.app.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static com.ucamp.app.support.SurveyFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

// STEP 09: 알림 발송 dedup·분산, 알림 진입 재사용 조회, 큐 구현
@SpringBootTest
class NotificationAndReuseTest {
    @Autowired
    RecommendationReuseService reuseService;
    @Autowired
    RecommendationService recommendationService;
    @Autowired
    SurveySubmitService surveySubmitService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    com.ucamp.app.infra.llm.FakeLlmClient fake;

    @org.junit.jupiter.api.AfterEach
    void resetFake() {
        fake.reset();
    }

    @Test
    void 같은_날_같은_알림은_한_번만_계획된다() {
        NotificationSpikeSimulator sim = new NotificationSpikeSimulator();
        LocalDateTime start = LocalDateTime.of(2026, 10, 21, 9, 0);

        List<PlannedNotification> first = sim.plan(List.of(1L, 2L, 3L), start, Duration.ZERO);
        List<PlannedNotification> again = sim.plan(List.of(2L, 3L, 4L), start.plusHours(3), Duration.ZERO);
        List<PlannedNotification> nextDay = sim.plan(List.of(1L), start.plusDays(1), Duration.ZERO);

        assertThat(first).extracting(PlannedNotification::userId).containsExactly(1L, 2L, 3L);
        assertThat(again).extracting(PlannedNotification::userId).containsExactly(4L);
        assertThat(nextDay).hasSize(1);
        assertThat(first.get(0).dedupKey()).isEqualTo("1:NUTRIENT_REMINDER:20261021");
        assertThat(NotificationSpikeSimulator.dedupKey(1L, "X", LocalDate.of(2026, 1, 2))).isEqualTo("1:X:20260102");
    }

    @Test
    void 발송_분산은_구간에_고르게_흩는다() {
        NotificationSpikeSimulator sim = new NotificationSpikeSimulator();
        LocalDateTime start = LocalDateTime.of(2026, 10, 21, 9, 0);
        List<Long> users = LongStream.rangeClosed(1, 600).boxed().toList();

        List<PlannedNotification> burst = new NotificationSpikeSimulator().plan(users, start, Duration.ZERO);
        List<PlannedNotification> spread = sim.plan(users, start, Duration.ofMinutes(30));

        assertThat(NotificationSpikeSimulator.perMinute(burst, start)).containsExactly(Map.entry(0L, 600));
        Map<Long, Integer> perMinute = NotificationSpikeSimulator.perMinute(spread, start);
        assertThat(perMinute).hasSize(30);
        assertThat(perMinute.values()).allMatch(c -> c == 20);
        LoggingNotificationSender sender = new LoggingNotificationSender();
        sim.sendAll(spread, sender);
        assertThat(sender.sentCount()).isEqualTo(600);
    }

    @Test
    void 알림_진입_재사용_조회() {
        User user = TestUsers.create(userRepository, true);
        assertThat(reuseService.latest(user.getId()).reason()).isEqualTo(RecommendationReuseService.NO_RECOMMENDATION);

        long responseId = surveySubmitService.submit(SURVEY_ID, user.getId(), new SubmitSurveyRequest(List.of(
                new SubmitSurveyRequest.Answer(Q_GENDER, List.of(GENDER_M), null, null),
                new SubmitSurveyRequest.Answer(Q_SMOKING, List.of(SMOKING_NONE), null, null)))).responseId();
        recommendationService.create(responseId);

        LatestRecommendationResponse reusable = reuseService.latest(user.getId());
        assertThat(reusable.reusable()).isTrue();
        assertThat(reusable.needsNewSurvey()).isFalse();
        assertThat(reusable.recommendation().responseId()).isEqualTo(responseId);

        // 추천 이후 프로필이 바뀌면 새 설문 안내
        jdbcTemplate.update("update health_profiles set updated_at = ? where user_id = ?",
                LocalDateTime.now().plusMinutes(1), user.getId());
        LatestRecommendationResponse changed = reuseService.latest(user.getId());
        assertThat(changed.reusable()).isFalse();
        assertThat(changed.needsNewSurvey()).isTrue();
        assertThat(changed.reason()).isEqualTo(RecommendationReuseService.PROFILE_CHANGED);
    }

    @Test
    void 메모리_큐는_FIFO() throws Exception {
        RecommendationQueue q = new InMemoryRecommendationQueue();
        q.enqueue(1);
        q.enqueue(2);

        assertThat(q.size()).isEqualTo(2);
        assertThat(q.poll(Duration.ofMillis(10))).contains(1L);
        assertThat(q.poll(Duration.ofMillis(10))).contains(2L);
        assertThat(q.poll(Duration.ofMillis(10))).isEmpty();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "REDIS_HOST", matches = ".+")
    void Redis_List_큐는_FIFO() throws Exception {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(System.getenv("REDIS_HOST"), 6379));
        factory.afterPropertiesSet();
        try {
            StringRedisTemplate template = new StringRedisTemplate(factory);
            template.delete("rec:queue");
            RecommendationQueue q = new RedisRecommendationQueue(template);
            q.enqueue(10);
            q.enqueue(11);

            assertThat(q.size()).isEqualTo(2);
            assertThat(q.poll(Duration.ofSeconds(1))).contains(10L);
            assertThat(q.poll(Duration.ofSeconds(1))).contains(11L);
            assertThat(q.poll(Duration.ofSeconds(1))).isEmpty();
        } finally {
            factory.destroy();
        }
    }
}
