package com.ucamp.app.domain.chat.parse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5 ServerDrugDetector 단위 테스트: 서버 약 이름 탐지.
 */
class ServerDrugDetectorTest {

    static DrugCatalog.Entry entry(String id, String name, String cls) {
        return new DrugCatalog.Entry(id, name, cls);
    }

    static final List<DrugCatalog.Entry> DRUGS = List.of(
            entry("1", "와파린", "WARFARIN"),
            entry("2", "아토르바스타틴(리피토)", "STATIN"),
            entry("3", "메트포르민", "METFORMIN"),
            entry("4", "(이름 모름)", "UNKNOWN"),
            entry("5", "아", "ASPIRIN")  // 1자 → 건너뜀
    );

    @Test
    @DisplayName("일반 약 이름 탐지")
    void detects_drug_name() {
        List<DrugCatalog.Entry> found = ServerDrugDetector.detect("와파린 먹고 있어요", DRUGS);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).drugId()).isEqualTo("1");
    }

    @Test
    @DisplayName("괄호 부분 제외하고 탐지")
    void excludes_parenthesized_part() {
        // "아토르바스타틴(리피토)" → 괄호 제거 후 "아토르바스타틴"으로 매칭
        List<DrugCatalog.Entry> found = ServerDrugDetector.detect("아토르바스타틴 처방받았어요", DRUGS);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).drugId()).isEqualTo("2");
    }

    @Test
    @DisplayName("nameUnknown() 항목 건너뜀")
    void skips_name_unknown() {
        List<DrugCatalog.Entry> found = ServerDrugDetector.detect("이름 모름", DRUGS);
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("2자 미만 이름 건너뜀")
    void skips_short_name() {
        List<DrugCatalog.Entry> found = ServerDrugDetector.detect("아 먹고 있어요", DRUGS);
        // "아"는 1자라서 건너뜀
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("여러 약 동시 탐지")
    void detects_multiple() {
        List<DrugCatalog.Entry> found = ServerDrugDetector.detect("와파린이랑 메트포르민 먹어요", DRUGS);
        assertThat(found).extracting(DrugCatalog.Entry::drugId).containsExactlyInAnyOrder("1", "3");
    }

    @Test
    @DisplayName("같은 drugId 중복 방지")
    void deduplicates_by_drug_id() {
        List<DrugCatalog.Entry> drugs = List.of(
                entry("1", "와파린", "WARFARIN"),
                entry("1", "와파린정", "WARFARIN")
        );
        List<DrugCatalog.Entry> found = ServerDrugDetector.detect("와파린 먹어요", drugs);
        assertThat(found).hasSize(1);
    }

    @Test
    @DisplayName("빈 입력 → 빈 결과")
    void empty_input() {
        assertThat(ServerDrugDetector.detect(null, DRUGS)).isEmpty();
        assertThat(ServerDrugDetector.detect("", DRUGS)).isEmpty();
        assertThat(ServerDrugDetector.detect("  ", DRUGS)).isEmpty();
    }

    @Test
    @DisplayName("약 없는 문장 → 빈 결과")
    void no_drugs_found() {
        assertThat(ServerDrugDetector.detect("요즘 피곤해요", DRUGS)).isEmpty();
    }
}
