package com.ucamp.app.domain.review;

import com.ucamp.app.domain.review.config.ReviewProductProperties;
import com.ucamp.app.domain.review.dto.ReviewProductResponse;
import com.ucamp.app.domain.review.safety.ProductSafetyFilter;
import com.ucamp.app.domain.review.service.RecommendationConditionLoader;
import com.ucamp.app.domain.review.service.ReviewProductRecommendationService;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 리뷰 제품 추천 정렬·제한·필터 (테스트 데이터 sql/seed-review-products.sql, 예시 데이터).
 * 추천 결과 → 사용자 조건 변환은 API 테스트에서 확인하고, 여기서는 조건을 직접 넣는다. 규칙은 테스트 시드(RuleService).
 * DDL은 H2에서 자동 커밋되므로 별도 트랜잭션(ISOLATED)으로 실행하고, 데이터는 테스트 트랜잭션 안에서 넣고 롤백한다
 */
@SpringBootTest
@Transactional
@Sql(scripts = "/sql/schema-review-products.sql", config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
@Sql("/sql/seed-review-products.sql")
class ReviewProductRecommendationServiceTest {
    static final long REC_ID = 1L;
    static final String RK02_MSG = "신장 기능이 떨어져 있으면 마그네슘이 몸에 쌓일 수 있어요. 의사·약사와 상담하세요";

    @Autowired
    ReviewProductRecommendationService service;
    @Autowired
    ReviewProductProperties properties;
    @Autowired
    MeterRegistry meterRegistry;
    @MockitoBean
    RecommendationConditionLoader loader;

    @AfterEach
    void resetLimits() {
        properties.getProducts().setMax(3);
        properties.getProducts().setMaxPerBrand(2);
    }

    void given(Set<String> conditions, boolean guest, boolean hasMedicationOrDisease, String... recommended) {
        List<String> items = List.of(recommended);
        when(loader.load(anyLong())).thenReturn(new RecommendationConditionLoader.Loaded(items,
                new ProductSafetyFilter.UserConditions(conditions, guest, hasMedicationOrDisease, items)));
    }

    static List<Long> ids(ReviewProductResponse r) {
        return r.products().stream().map(ReviewProductResponse.Product::catalogProductId).toList();
    }

    @Test
    void 최대_3개_같은_브랜드_최대_2개_동점은_최신_추천일_먼저() {
        given(Set.of(), false, false, "MAGNESIUM");

        ReviewProductResponse r = service.recommend(REC_ID, "MAGNESIUM");

        // 9303(NOW 세 번째)은 브랜드 제한, 9305·9304 동점(2.0, 약사 2명) → 마지막 추천일 9/15인 9305
        assertThat(ids(r)).containsExactly(9301L, 9302L, 9305L);
        assertThat(r.excludedCount()).isEqualTo(1); // 9310 라벨 성분 없음
        assertThat(r.message()).isNull();
        assertThat(r.notice()).isEqualTo(ReviewProductResponse.MEMBER_NOTICE);
        ReviewProductResponse.Product first = r.products().get(0);
        assertThat(first.productName()).isEqualTo("나우 마그네슘 글리시네이트"); // name_ko 우선
        assertThat(first.brand()).isEqualTo("NOW");
        assertThat(first.cautions()).containsExactly("알약이 클 수 있어요");
        assertThat(first.alsoContains()).isEmpty();
        assertThat(first.warnings()).isEmpty();
        assertThat(r.products().get(1).productName()).isEqualTo("Magnesium Citrate"); // name_ko 없으면 product_name
        assertThat(r.products().get(2).cautions()).containsExactly("공복에 먹으면 속이 불편할 수 있어요"); // {category, text}
    }

    @Test
    void 전체_정렬_순서와_차단_비활성_리뷰없는_제품_제외() {
        properties.getProducts().setMax(20);
        properties.getProducts().setMaxPerBrand(20);
        given(Set.of(), false, false, "MAGNESIUM");

        ReviewProductResponse r = service.recommend(REC_ID, "MAGNESIUM");

        // 9307 차단·9308 비활성은 점수가 높아도 없음, 9313은 라벨에 마그네슘이 있어도 마그네슘 리뷰가 없어 없음
        assertThat(ids(r)).containsExactly(9301L, 9302L, 9303L, 9305L, 9304L, 9309L, 9306L);
        assertThat(r.products().get(5).warnings()).containsExactly(ProductSafetyFilter.UNVERIFIED_WARNING);
    }

    @Test
    void 같은_추천_결과의_다른_성분이_같이_들면_커버리지_가점과_alsoContains() {
        given(Set.of(), false, false, "MAGNESIUM", "VITAMIN_B6");

        ReviewProductResponse r = service.recommend(REC_ID, "MAGNESIUM");

        // 9306: 1.2 + 1.0 = 2.2 → 2.0 동점 제품들보다 앞
        assertThat(ids(r)).containsExactly(9301L, 9302L, 9306L);
        assertThat(r.products().get(2).alsoContains()).containsExactly("VITAMIN_B6");
        assertThat(r.products().get(2).imageUrl()).isEqualTo("https://example.com/9306.png");
    }

    @Test
    void 찾는_성분_WARN은_경고와_감점_OTHER_성분은_질환이_있으면_제외() {
        given(Set.of("KIDNEY"), false, true, "MAGNESIUM");

        ReviewProductResponse r = service.recommend(REC_ID, "MAGNESIUM");

        assertThat(ids(r)).containsExactly(9301L, 9302L, 9305L);
        assertThat(r.products()).allSatisfy(p -> assertThat(p.warnings()).containsExactly(RK02_MSG));
        assertThat(r.excludedCount()).isEqualTo(2); // 9309 OTHER + 질환, 9310 성분 없음
    }

    @Test
    void 와파린이면_비타민K가_같이_든_비타민D_제품은_제외() {
        given(Set.of("WARFARIN"), false, true, "VITAMIN_D");

        ReviewProductResponse r = service.recommend(REC_ID, "VITAMIN_D");

        assertThat(ids(r)).containsExactly(9312L);
        assertThat(r.excludedCount()).isEqualTo(1);
    }

    @Test
    void 추천_결과에_없는_성분은_빈_목록() {
        given(Set.of(), false, false, "MAGNESIUM");

        ReviewProductResponse r = service.recommend(REC_ID, "VITAMIN_D");

        assertThat(r.products()).isEmpty();
        assertThat(r.excludedCount()).isZero();
        assertThat(r.message()).isEqualTo(ReviewProductResponse.EMPTY_MESSAGE);
    }

    @Test
    void 리뷰_점수가_없는_성분은_빈_목록과_empty_메트릭() {
        given(Set.of("WARFARIN"), false, true, "VITAMIN_K");
        double before = count("review.products.result", "outcome", "empty");

        ReviewProductResponse r = service.recommend(REC_ID, "VITAMIN_K"); // VITAMIN_K 리뷰 점수 없음

        assertThat(r.products()).isEmpty();
        assertThat(r.message()).isEqualTo(ReviewProductResponse.EMPTY_MESSAGE);
        assertThat(count("review.products.result", "outcome", "empty")).isEqualTo(before + 1);
    }

    @Test
    void 후보가_모두_제외되면_빈_목록과_all_excluded_메트릭() {
        // 9313(B6 + 마그네슘): 신장 질환이면 같이 든 마그네슘이 WARN → 제외
        given(Set.of("KIDNEY"), false, true, "VITAMIN_B6");
        double before = count("review.products.result", "outcome", "all_excluded");
        double rule = count("review.safety.excluded", "reason", "rule");

        ReviewProductResponse r = service.recommend(REC_ID, "VITAMIN_B6");

        assertThat(r.products()).isEmpty();
        assertThat(r.excludedCount()).isEqualTo(1);
        assertThat(r.message()).isEqualTo(ReviewProductResponse.EMPTY_MESSAGE);
        assertThat(count("review.products.result", "outcome", "all_excluded")).isEqualTo(before + 1);
        assertThat(count("review.safety.excluded", "reason", "rule")).isEqualTo(rule + 1);
    }

    @Test
    void 비회원은_안내_문구에_확인_범위_안내를_더한다() {
        given(Set.of("GUEST"), true, false, "MAGNESIUM");

        ReviewProductResponse r = service.recommend(REC_ID, "MAGNESIUM");

        assertThat(r.notice()).isEqualTo(ReviewProductResponse.MEMBER_NOTICE + ReviewProductResponse.GUEST_NOTICE_SUFFIX);
        assertThat(ids(r)).containsExactly(9301L, 9302L, 9305L);
    }

    @Test
    void 잘못된_성분_코드는_400() {
        assertThatThrownBy(() -> service.recommend(REC_ID, "MELATONIN"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
        assertThatThrownBy(() -> service.recommend(REC_ID, null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void availability는_성분마다_통과_제품이_있는지() {
        given(Set.of("WARFARIN"), false, true, "MAGNESIUM", "VITAMIN_D", "ZINC");

        assertThat(service.availability(REC_ID))
                .containsExactly(java.util.Map.entry("MAGNESIUM", true), java.util.Map.entry("VITAMIN_D", true),
                        java.util.Map.entry("ZINC", false));
    }

    @Test
    void 결과_메트릭과_제외_사유_메트릭() {
        given(Set.of(), false, false, "MAGNESIUM");
        double ok = count("review.products.result", "outcome", "ok");
        double unknown = count("review.safety.excluded", "reason", "unknown_ingredients");

        service.recommend(REC_ID, "MAGNESIUM");

        assertThat(count("review.products.result", "outcome", "ok")).isEqualTo(ok + 1);
        assertThat(count("review.safety.excluded", "reason", "unknown_ingredients")).isEqualTo(unknown + 1);
        assertThat(meterRegistry.find("review.products.request").tag("api", "products").timer()).isNotNull();
    }

    double count(String name, String tag, String value) {
        var c = meterRegistry.find(name).tag(tag, value).counter();
        return c == null ? 0 : c.count();
    }
}
