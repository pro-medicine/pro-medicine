package com.ucamp.app.domain.recommendation;

import com.ucamp.app.domain.chat.entity.AiChatResult;
import com.ucamp.app.domain.chat.entity.AiChatSession;
import com.ucamp.app.domain.chat.repository.AiChatResultRepository;
import com.ucamp.app.domain.chat.repository.AiChatSessionRepository;
import com.ucamp.app.domain.personalized.entity.AiQuestionSession;
import com.ucamp.app.domain.personalized.repository.AiQuestionSessionRepository;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.entity.AiRecommendationItem;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationItemRepository;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationRepository;
import com.ucamp.app.domain.review.repository.ReviewProductQueryRepository;
import com.ucamp.app.domain.user.entity.User;
import com.ucamp.app.support.TestMembers;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.ucamp.app.support.TestJwt.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V7 /recent 요청당 DB 쿼리 수 측정 (보고서용). JPA는 Hibernate 통계(준비한 SQL 수),
 * 제품 후보·라벨 조회(JdbcTemplate)는 ReviewProductQueryRepository 호출 수로 센다.
 * 통계·스파이 빈 때문에 컨텍스트가 따로 뜨므로 RecentRecommendationApiTest와 분리했다.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Import(TestMembers.class)
@Transactional
@Sql(scripts = "/sql/schema-review-products.sql", config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
@Sql("/sql/seed-review-products.sql")
class RecentRecommendationQueryCountTest {
    static final String URL = "/api/v1/users/me/recommendations/recent";
    static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 9, 0);

    @Autowired
    MockMvc mockMvc;
    @Autowired
    TestMembers members;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    EntityManager entityManager;
    @Autowired
    EntityManagerFactory entityManagerFactory;
    @Autowired
    AiQuestionSessionRepository questionSessionRepository;
    @Autowired
    AiChatSessionRepository chatSessionRepository;
    @Autowired
    AiChatResultRepository chatResultRepository;
    @Autowired
    AiRecommendationRepository recommendationRepository;
    @Autowired
    AiRecommendationItemRepository itemRepository;
    @MockitoSpyBean
    ReviewProductQueryRepository productQueryRepository;

    @Test
    void 요청당_DB_쿼리_수() throws Exception {
        User user = members.create(true, Map.of("gender", "F", "birth_year", 1995), List.of(), List.of());
        long one = v2(user, "OMEGA3");
        setTime(one, T0);
        int single = count(user);
        long three = v2(user, "OMEGA3", "MAGNESIUM", "VITAMIN_D");
        setTime(three, T0.plusHours(1));
        int triple = count(user);
        AiChatSession chat = chatSessionRepository.save(AiChatSession.start(user));
        AiChatResult result = chatResultRepository.save(AiChatResult.builder().chatSession(chat)
                .track(AiChatResult.REVIEW).cards("{\"cards\":[]}").build());
        entityManager.flush();
        jdbcTemplate.update("update ai_chat_results set created_at = ? where chat_result_id = ?",
                Timestamp.valueOf(T0.plusHours(2)), result.getChatResultId());
        int snapshot = count(user);

        System.out.printf("[V7] queries per /recent: V2 1 ingredient=%d, V2 3 ingredients=%d, V4 track1 snapshot=%d%n",
                single, triple, snapshot);
        assertThat(triple).isGreaterThan(single);
        assertThat(snapshot).isEqualTo(2); // 후보 ①·② 조회만
    }

    int count(User user) throws Exception {
        entityManager.flush();
        entityManager.clear();
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        clearInvocations(productQueryRepository);
        mockMvc.perform(get(URL).header("Authorization", bearer(user.getId()))).andExpect(status().isOk());
        long jpa = stats.getPrepareStatementCount();
        long jdbc = mockingDetails(productQueryRepository).getInvocations().stream()
                .filter(i -> i.getMethod().getName().startsWith("find")).count();
        return (int) (jpa + jdbc);
    }

    long v2(User user, String... ingredients) {
        AiQuestionSession session = questionSessionRepository.save(AiQuestionSession.start(user));
        AiRecommendation rec = recommendationRepository.save(AiRecommendation.builder().session(session)
                .summary("가짜 정밀 추천").modelName("fake").status(AiRecommendation.DONE).build());
        for (int i = 0; i < ingredients.length; i++) {
            itemRepository.save(AiRecommendationItem.builder().recommendation(rec).ingredientName(ingredients[i])
                    .reason("가짜 응답").sortOrder(i + 1).build());
        }
        return rec.getRecommendationId();
    }

    void setTime(long recId, LocalDateTime at) {
        entityManager.flush();
        jdbcTemplate.update("update ai_recommendations set created_at = ?, finished_at = ? where recommendation_id = ?",
                Timestamp.valueOf(at.minusSeconds(5)), Timestamp.valueOf(at), recId);
    }
}
