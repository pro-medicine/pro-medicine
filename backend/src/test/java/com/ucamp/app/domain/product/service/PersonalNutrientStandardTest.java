package com.ucamp.app.domain.product.service;

import com.ucamp.app.domain.product.enums.Nutrient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 설문 결과로 정하는 사람별 하루 권장량 테스트 (2025 한국인 영양소 섭취기준, 테스트 시드 seed-ingredient-standards.sql).
 * 데이터는 sql/personal-standard-fixture.sql, 기준 날짜 2026-11-03
 */
@SpringBootTest
@Transactional
@Sql("/sql/personal-standard-fixture.sql")
class PersonalNutrientStandardTest {

    private static final LocalDate NOV_3 = LocalDate.of(2026, 11, 3);

    @Autowired
    private PersonalNutrientStandardService service;

    @Test
    @DisplayName("설문 정보가 없으면 2025 성인 19~29세 남성 기준")
    void defaultIsMale19to29() {
        Map<Nutrient, Double> r = service.recommendedAmounts(9702L, NOV_3);

        assertThat(r).containsEntry(Nutrient.MAGNESIUM, 360.0)
                .containsEntry(Nutrient.IRON, 8.0)
                .containsEntry(Nutrient.CALCIUM, 800.0)
                .containsEntry(Nutrient.OMEGA3, 250.0)
                .containsEntry(Nutrient.VITAMIN_B2, 1.5)   // STEP13으로 추가한 기준
                .containsEntry(Nutrient.VITAMIN_E, 12.0)
                .containsEntry(Nutrient.VITAMIN_C, 100.0);
        assertThat(r).hasSize(Nutrient.values().length);   // 14개 성분 모두
    }

    @Test
    @DisplayName("여성 30세 + 임신 중(v2 맞춤 질문 답): 성별·나이·임신 기준이 적용된다")
    void femalePregnant() {
        Map<Nutrient, Double> r = service.recommendedAmounts(9701L, NOV_3);

        assertThat(r).containsEntry(Nutrient.IRON, 12.0)        // 여성 19~49세
                .containsEntry(Nutrient.MAGNESIUM, 280.0)
                .containsEntry(Nutrient.CALCIUM, 650.0)
                .containsEntry(Nutrient.VITAMIN_B2, 1.2)
                .containsEntry(Nutrient.FOLATE, 550.0)          // 임신·수유
                .containsEntry(Nutrient.VITAMIN_C, 110.0)
                .containsEntry(Nutrient.OMEGA3, 300.0);
    }

    @Test
    @DisplayName("남성 56세 흡연자: 나이대 기준과 흡연자 비타민 C 기준이 적용된다")
    void maleSmoker56() {
        Map<Nutrient, Double> r = service.recommendedAmounts(9703L, NOV_3);

        assertThat(r).containsEntry(Nutrient.VITAMIN_C, 135.0)    // 흡연자
                .containsEntry(Nutrient.MAGNESIUM, 380.0)          // 남성 30세 이상
                .containsEntry(Nutrient.IRON, 8.0);
    }

    @Test
    @DisplayName("임신 여부는 가장 최근 설문 답을 쓴다 (답이 없으면 null)")
    void latestPregnancy() {
        assertThat(service.latestPregnancy(9701L)).isEqualTo("PREGNANT");
        assertThat(service.latestPregnancy(9702L)).isNull();
    }
}
