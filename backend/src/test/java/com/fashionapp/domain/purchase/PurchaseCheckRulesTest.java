package com.fashionapp.domain.purchase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FR-11 verdict rule: SIMILAR ≥ threshold, otherwise BUY when +N ≥ buy-min-plus, otherwise THINK. */
class PurchaseCheckRulesTest {

    private final PurchaseCheckRules rules = new PurchaseCheckRules(0.85, 5);

    @Test
    @DisplayName("유사도가 정확히 기준값이면 SIMILAR (부동소수 오차로 조금 모자라도 같은 값으로 본다)")
    void thresholdIsInclusive() {
        assertThat(rules.verdict(OptionalDouble.of(0.85), 10)).isEqualTo(PurchaseVerdict.SIMILAR);
        assertThat(rules.verdict(OptionalDouble.of(0.8499999999), 10)).isEqualTo(PurchaseVerdict.SIMILAR);
        assertThat(rules.verdict(OptionalDouble.of(0.95), 0)).isEqualTo(PurchaseVerdict.SIMILAR);
    }

    @Test
    @DisplayName("기준값보다 낮으면 +N으로 판정: 5 이상 BUY, 4 이하 THINK")
    void belowThresholdUsesPlus() {
        assertThat(rules.verdict(OptionalDouble.of(0.84), 5)).isEqualTo(PurchaseVerdict.BUY);
        assertThat(rules.verdict(OptionalDouble.of(0.84), 4)).isEqualTo(PurchaseVerdict.THINK);
        assertThat(rules.verdict(OptionalDouble.empty(), 5)).isEqualTo(PurchaseVerdict.BUY);
        assertThat(rules.verdict(OptionalDouble.empty(), 0)).isEqualTo(PurchaseVerdict.THINK);
    }

    @Test
    @DisplayName("설정값을 바꾸면 기준도 바뀐다")
    void configurable() {
        PurchaseCheckRules strict = new PurchaseCheckRules(0.9, 10);
        assertThat(strict.verdict(OptionalDouble.of(0.88), 9)).isEqualTo(PurchaseVerdict.THINK);
        assertThat(strict.verdict(OptionalDouble.of(0.88), 10)).isEqualTo(PurchaseVerdict.BUY);
    }

    @Test
    @DisplayName("기준값이 범위를 벗어나면 기동 실패")
    void invalidConfig() {
        assertThatThrownBy(() -> new PurchaseCheckRules(0, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PurchaseCheckRules(1.2, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PurchaseCheckRules(0.85, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
