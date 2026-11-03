package com.ucamp.app.domain.product.service;

import com.ucamp.app.domain.intake.repository.IntakeScheduleRepository;
import com.ucamp.app.domain.product.dto.MySupplementAddRequest;
import com.ucamp.app.domain.product.dto.MySupplementResponse;
import com.ucamp.app.domain.product.dto.NutrientIntakeResponse;
import com.ucamp.app.domain.product.dto.NutritionResponse;
import com.ucamp.app.domain.product.dto.ScheduleRequest;
import com.ucamp.app.domain.product.dto.SupplementSearchResponse;
import com.ucamp.app.domain.product.dto.TodayNutritionResponse;
import com.ucamp.app.domain.product.dto.TodayNutritionResponse.ScheduleNutrients;
import com.ucamp.app.global.exception.BusinessException;
import com.ucamp.app.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 엑셀 영양제(supplements) 검색·등록·홈 "오늘 먹은 영양소" 테스트.
 * 데이터는 sql/supplement-fixture.sql (사용자 9601, 2026-11-03)
 * - 영양제 9601: 1일 2회, 하루 비타민D 10㎍·아연 8mg → 아침·저녁 일정마다 절반(1회분)씩
 * - 영양제 9602: 함량 정보 없음 → 계산에서 빠지고 unsupportedCount
 */
@SpringBootTest
@Transactional
@Sql("/sql/supplement-fixture.sql")
class SupplementTest {

    private static final LocalDate NOV_3 = LocalDate.of(2026, 11, 3);

    @Autowired
    private TodayNutritionService todayNutritionService;

    @Autowired
    private SupplementService supplementService;

    @Autowired
    private MySupplementService mySupplementService;

    @Autowired
    private IntakeScheduleRepository intakeScheduleRepository;

    @Autowired
    private NutritionService nutritionService;

    @Test
    @DisplayName("홈 영양소: 엑셀 영양제는 하루 함량(1회×횟수)을 일정 수로 나눠 일정마다 보여준다")
    void todayNutritionFromExcel() {
        TodayNutritionResponse r = todayNutritionService.getTodayNutrition(9601L, NOV_3);

        assertThat(r.schedules()).extracting(ScheduleNutrients::scheduleId).containsExactly(9601L, 9602L);
        assertThat(amountsOf(r, 9601L)).containsExactlyInAnyOrderEntriesOf(Map.of("VITAMIN_D", 5.0, "ZINC", 4.0));
        assertThat(amountsOf(r, 9602L)).containsExactlyInAnyOrderEntriesOf(Map.of("VITAMIN_D", 5.0, "ZINC", 4.0));
        assertThat(r.unsupportedCount()).isEqualTo(1);   // 함량 정보 없는 영양제 9602
        assertThat(r.sample()).isFalse();
    }

    @Test
    @DisplayName("마이페이지 영양관리: 엑셀 영양제를 등록만 해도 하루 함량으로 게이지를 채우고, 권장량은 개인 기준(정보 없으면 19~29세 남성)")
    void myPageNutritionFromExcel() {
        NutritionResponse r = nutritionService.getNutrition(9601L);

        assertThat(r.nutrients()).extracting(NutrientIntakeResponse::name).containsExactly("비타민 D", "아연");
        NutrientIntakeResponse vitaminD = r.nutrients().get(0);
        assertThat(vitaminD.intakeAmount()).isEqualTo(10.0);       // 1회 5㎍ × 1일 2회
        assertThat(vitaminD.recommendedAmount()).isEqualTo(10.0);  // KDRI-2025 19~64세 공통
        assertThat(vitaminD.percent()).isEqualTo(100);
        assertThat(vitaminD.sources()).singleElement().satisfies(src -> {
            assertThat(src.userProductId()).isEqualTo(9601L);
            assertThat(src.productName()).isEqualTo("테스트 비타민D 아연");
        });
        NutrientIntakeResponse zinc = r.nutrients().get(1);
        assertThat(zinc.intakeAmount()).isEqualTo(8.0);
        assertThat(zinc.recommendedAmount()).isEqualTo(10.0);      // 성별 정보 없음 → 남성 19~64세
        assertThat(zinc.percent()).isEqualTo(80);
        assertThat(r.unsupportedCount()).isEqualTo(1);             // 함량 정보 없는 영양제 9602
        assertThat(r.sample()).isFalse();
    }

    @Test
    @DisplayName("마이페이지 영양관리: 새로 등록한 엑셀 영양제가 바로 반영된다")
    void myPageNutritionRightAfterAdd() {
        mySupplementService.addMySupplement(9601L, new MySupplementAddRequest(
                null, null, List.of(new ScheduleRequest("BREAKFAST", "AFTER", LocalTime.of(8, 0))), 9603L));

        NutritionResponse r = nutritionService.getNutrition(9601L);
        assertThat(r.nutrients()).extracting(NutrientIntakeResponse::name).contains("오메가3");
    }

    @Test
    @DisplayName("홈 타임라인 이름: 엑셀 영양제는 제품명을 보여준다")
    void timelineName() {
        List<String> names = intakeScheduleRepository.findTodaySupplements(9601L, NOV_3).stream()
                .map(IntakeScheduleRepository.SupplementRow::getName)
                .toList();
        assertThat(names).contains("테스트 비타민D 아연", "테스트 함량없음");
    }

    @Test
    @DisplayName("검색: 제품명 또는 제조사로 찾고, 섭취 방법·하루 성분 요약을 함께 준다")
    void search() {
        List<SupplementSearchResponse> byName = supplementService.search("테스트 비타민D");
        assertThat(byName).singleElement().satisfies(s -> {
            assertThat(s.supplementId()).isEqualTo(9601L);
            assertThat(s.intakeMethod()).isEqualTo("1일 2회");
            assertThat(s.nutrientSummary()).isEqualTo("비타민 D 10㎍ · 아연 8mg");
        });
        assertThat(supplementService.search("다른회사")).extracting(SupplementSearchResponse::supplementId)
                .containsExactly(9603L);
        assertThatThrownBy(() -> supplementService.search("  ")).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("등록: 엑셀 영양제 id로 등록하고, 같은 영양제를 또 등록하면 409")
    void addExcelSupplement() {
        MySupplementResponse added = mySupplementService.addMySupplement(9601L, new MySupplementAddRequest(
                null, null, List.of(new ScheduleRequest("BREAKFAST", "AFTER", LocalTime.of(8, 0))), 9603L));

        assertThat(added.supplementId()).isEqualTo(9603L);
        assertThat(added.catalogProductId()).isNull();
        assertThat(added.productName()).isEqualTo("테스트 오메가");
        assertThat(added.ingredientSummary()).isEqualTo("오메가3 600mg");
        assertThat(added.intakeMethod()).isEqualTo("1일 1회");
        assertThat(added.schedules()).hasSize(1);

        assertThatThrownBy(() -> mySupplementService.addMySupplement(9601L,
                new MySupplementAddRequest(null, null, List.of(), 9603L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_ALREADY_ADDED);
    }

    @Test
    @DisplayName("등록: 엑셀 영양제 id와 다른 값(제품 id·직접 입력)을 같이 보내면 400")
    void addRequiresExactlyOne() {
        assertThatThrownBy(() -> mySupplementService.addMySupplement(9601L,
                new MySupplementAddRequest(null, "직접입력", List.of(), 9603L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private static Map<String, Double> amountsOf(TodayNutritionResponse r, long scheduleId) {
        return r.schedules().stream().filter(s -> s.scheduleId() == scheduleId).findFirst().orElseThrow().amounts();
    }
}
