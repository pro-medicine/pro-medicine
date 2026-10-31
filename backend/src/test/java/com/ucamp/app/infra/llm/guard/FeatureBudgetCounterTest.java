package com.ucamp.app.infra.llm.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5 FeatureBudgetCounter 단위 테스트: 메모리 예산 카운터.
 */
class FeatureBudgetCounterTest {

    @Test
    @DisplayName("increment 후 get으로 카운트 조회")
    void increment_and_get() {
        FeatureBudgetCounter counter = new FeatureBudgetCounter(null);
        assertThat(counter.get("chat")).isZero();
        counter.increment("chat");
        counter.increment("chat");
        assertThat(counter.get("chat")).isEqualTo(2);
    }

    @Test
    @DisplayName("기능별 독립 카운트")
    void independent_features() {
        FeatureBudgetCounter counter = new FeatureBudgetCounter(null);
        counter.increment("chat");
        counter.increment("chat");
        counter.increment("recommendation");
        assertThat(counter.get("chat")).isEqualTo(2);
        assertThat(counter.get("recommendation")).isEqualTo(1);
        assertThat(counter.get("personalized")).isZero();
    }

    @Test
    @DisplayName("increment는 증가 후 값을 반환")
    void increment_returns_new_value() {
        FeatureBudgetCounter counter = new FeatureBudgetCounter(null);
        assertThat(counter.increment("chat")).isEqualTo(1);
        assertThat(counter.increment("chat")).isEqualTo(2);
        assertThat(counter.increment("chat")).isEqualTo(3);
    }
}
