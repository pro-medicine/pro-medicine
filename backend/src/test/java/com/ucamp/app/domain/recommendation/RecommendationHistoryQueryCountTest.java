package com.ucamp.app.domain.recommendation;

import com.ucamp.app.domain.chat.entity.AiChatResult;
import com.ucamp.app.domain.chat.entity.AiChatSession;
import com.ucamp.app.domain.chat.entity.AiChatTurn;
import com.ucamp.app.domain.chat.repository.AiChatResultRepository;
import com.ucamp.app.domain.chat.repository.AiChatSessionRepository;
import com.ucamp.app.domain.chat.repository.AiChatTurnRepository;
import com.ucamp.app.domain.personalized.entity.AiQuestionSession;
import com.ucamp.app.domain.personalized.entity.AiQuestionTurn;
import com.ucamp.app.domain.personalized.planner.OptionKind;
import com.ucamp.app.domain.personalized.repository.AiQuestionSessionRepository;
import com.ucamp.app.domain.personalized.repository.AiQuestionTurnRepository;
import com.ucamp.app.domain.personalized.service.TurnCodec;
import com.ucamp.app.domain.personalized.service.TurnOption;
import com.ucamp.app.domain.product.entity.CatalogProduct;
import com.ucamp.app.domain.recommendation.entity.AiRecommendation;
import com.ucamp.app.domain.recommendation.entity.AiRecommendationItem;
import com.ucamp.app.domain.recommendation.entity.AiRecommendationProduct;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationItemRepository;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationProductRepository;
import com.ucamp.app.domain.recommendation.repository.AiRecommendationRepository;
import com.ucamp.app.domain.recommendation.repository.RecommendationProductQueryRepository;
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
 * V7.1 추천 기록 API 요청당 DB 쿼리 수 (보고서용). JPA는 Hibernate 통계(준비한 SQL 수),
 * JdbcTemplate 조회는 RecommendationProductQueryRepository·ReviewProductQueryRepository 호출 수로 센다.
 * 목록은 출처별 일괄 조회라 건수가 늘어도 쿼리 수가 같아야 한다
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Import(TestMembers.class)
@Transactional
@Sql(scripts = "/sql/schema-review-products.sql", config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
@Sql("/sql/seed-review-products.sql")
class RecommendationHistoryQueryCountTest {
    static final String URL = "/api/v1/users/me/recommendations/history";
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
    TurnCodec turnCodec;
    @Autowired
    AiQuestionSessionRepository questionSessionRepository;
    @Autowired
    AiQuestionTurnRepository questionTurnRepository;
    @Autowired
    AiChatSessionRepository chatSessionRepository;
    @Autowired
    AiChatTurnRepository chatTurnRepository;
    @Autowired
    AiChatResultRepository chatResultRepository;
    @Autowired
    AiRecommendationRepository recommendationRepository;
    @Autowired
    AiRecommendationItemRepository itemRepository;
    @Autowired
    AiRecommendationProductRepository productRepository;
    @MockitoSpyBean
    ReviewProductQueryRepository reviewQueryRepository;
    @MockitoSpyBean
    RecommendationProductQueryRepository productQueryRepository;

    @Test
    void 요청당_DB_쿼리_수() throws Exception {
        User user = members.create(true, Map.of("gender", "F", "birth_year", 1995), List.of(), List.of());
        int minute = 0;
        // 3건 (출처마다 1건)
        long v2 = v2(user, T0.plusMinutes(minute++), "MAGNESIUM", "OMEGA3", "VITAMIN_D");
        track2(user, T0.plusMinutes(minute++), "VITAMIN_D");
        track1(user, T0.plusMinutes(minute++));
        int three = count(user, URL);
        // 20건 이상 (출처 섞음)
        for (int i = 0; i < 6; i++) {
            v2(user, T0.plusMinutes(minute++), "OMEGA3");
            track2(user, T0.plusMinutes(minute++), "VITAMIN_D");
            track1(user, T0.plusMinutes(minute++));
        }
        int twenty = count(user, URL);

        int recompute = count(user, URL + "/" + v2); // 저장된 순위 없음 → 성분 3개 다시 계산
        snapshot(v2, "MAGNESIUM", 9301L, 9302L);
        snapshot(v2, "OMEGA3", 9315L);
        int snapshot = count(user, URL + "/" + v2);
        long chatResultId = track1(user, T0.plusMinutes(minute));
        int chat = count(user, URL + "/chat/" + chatResultId);

        System.out.printf("[V7.1] queries: list 3 items=%d, list 20 items=%d, detail recompute(3 ingredients)=%d, "
                + "detail snapshot(3 ingredients)=%d, detail track1=%d%n", three, twenty, recompute, snapshot, chat);
        assertThat(twenty).isEqualTo(three); // 건수와 상관없이 같은 수
        assertThat(snapshot).isLessThan(recompute);
    }

    int count(User user, String url) throws Exception {
        entityManager.flush();
        entityManager.clear();
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        clearInvocations(reviewQueryRepository, productQueryRepository);
        mockMvc.perform(get(url).header("Authorization", bearer(user.getId()))).andExpect(status().isOk());
        long jpa = stats.getPrepareStatementCount();
        long jdbc = mockingDetails(reviewQueryRepository).getInvocations().stream()
                .filter(i -> i.getMethod().getName().startsWith("find")).count()
                + mockingDetails(productQueryRepository).getInvocations().stream()
                .filter(i -> i.getMethod().getName().startsWith("find")).count();
        return (int) (jpa + jdbc);
    }

    long v2(User user, LocalDateTime at, String... ingredients) {
        AiQuestionSession session = questionSessionRepository.save(AiQuestionSession.start(user));
        AiQuestionTurn turn = AiQuestionTurn.builder().session(session).turnNo(2).fixed(false).topic("CONCERN_OPEN")
                .depth(1).questionText("요즘 불편한 점을 골라주세요").answerType("MULTI")
                .optionsJson(turnCodec.encodeOptions(List.of(TurnOption.of("쉽게 피곤해요", OptionKind.CONCERN))))
                .build();
        turn.answer(turnCodec.encodeSelected(List.of(0)));
        questionTurnRepository.save(turn);
        AiRecommendation rec = recommendationRepository.save(AiRecommendation.builder().session(session).user(user)
                .summary("가짜").modelName("fake").status(AiRecommendation.DONE).build());
        items(rec, ingredients);
        setTime(rec.getRecommendationId(), at);
        return rec.getRecommendationId();
    }

    void track2(User user, LocalDateTime at, String... ingredients) {
        AiChatSession chat = chat(user);
        AiRecommendation rec = recommendationRepository.save(AiRecommendation.builder().chatSession(chat).user(user)
                .summary("가짜").modelName("fake").status(AiRecommendation.DONE).build());
        items(rec, ingredients);
        chatResultRepository.save(AiChatResult.builder().chatSession(chat).user(user).track(AiChatResult.RULE_BASED)
                .recommendationId(rec.getRecommendationId()).build());
        setTime(rec.getRecommendationId(), at);
    }

    long track1(User user, LocalDateTime at) {
        AiChatResult result = chatResultRepository.save(AiChatResult.builder().chatSession(chat(user)).user(user)
                .track(AiChatResult.REVIEW).cards("{\"cards\":[{\"ingredient\":\"MAGNESIUM\",\"sortOrder\":1,"
                        + "\"products\":[{\"catalogProductId\":9301,\"productName\":\"x\"}]}]}").build());
        entityManager.flush();
        jdbcTemplate.update("update ai_chat_results set created_at = ? where chat_result_id = ?", Timestamp.valueOf(at),
                result.getChatResultId());
        return result.getChatResultId();
    }

    AiChatSession chat(User user) {
        AiChatSession chat = chatSessionRepository.save(AiChatSession.start(user));
        chatTurnRepository.save(AiChatTurn.builder().chatSession(chat).turnNo(1).userText("가짜 고민")
                .llmCallLogIds("[]").build());
        return chat;
    }

    void items(AiRecommendation rec, String... ingredients) {
        for (int i = 0; i < ingredients.length; i++) {
            itemRepository.save(AiRecommendationItem.builder().recommendation(rec).ingredientName(ingredients[i])
                    .reason("가짜 응답").sortOrder(i + 1).build());
        }
    }

    void snapshot(long recId, String ingredient, Long... productIds) {
        entityManager.flush();
        AiRecommendationItem item = itemRepository
                .findByRecommendation_RecommendationIdOrderBySortOrderAscRecommendationItemIdAsc(recId).stream()
                .filter(i -> i.getIngredientName().equals(ingredient)).findFirst().orElseThrow();
        int rank = 1;
        for (Long id : productIds) {
            productRepository.save(AiRecommendationProduct.builder().item(item)
                    .product(entityManager.getReference(CatalogProduct.class, id)).productRank(rank++)
                    .alsoContains("[]").warnings("[]").build());
        }
    }

    void setTime(long recId, LocalDateTime at) {
        entityManager.flush();
        jdbcTemplate.update("update ai_recommendations set created_at = ?, finished_at = ? where recommendation_id = ?",
                Timestamp.valueOf(at.minusSeconds(5)), Timestamp.valueOf(at), recId);
    }
}
