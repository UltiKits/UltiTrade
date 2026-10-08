package com.ultikits.plugins.trade.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The experience tax for a rate that is not greater than zero
 * (<a href="https://github.com/UltiKits/UltiTrade/issues/65">UltiTrade#65</a> item 2; maintainer decision
 * 2026-10-06 00:48).
 *
 * <h2>The defect</h2>
 * {@code experienceTax} guarded {@code rate <= 0}, which is false for NaN, so {@code exp-tax-rate: .nan}
 * reached {@code BigDecimal.valueOf(NaN)} and threw -- in {@code completeTrade} after money had moved, and
 * in both trade windows. The guard is now {@code !(rate > 0)}: no tax for any rate that is not greater
 * than zero, NaN included. (The framework also refuses NaN at load since UltiTools-Reborn#625.)
 */
@DisplayName("No experience tax for a rate that is not greater than zero, NaN included (UltiTrade#65 item 2)")
class TradeExperienceTaxRateTest {

    @ParameterizedTest(name = "rate {0}")
    @ValueSource(doubles = {Double.NaN, -0.1, -0.0, 0.0, Double.NEGATIVE_INFINITY})
    @DisplayName("A rate that is not greater than zero takes no tax and does not throw")
    void noTaxForARateNotAboveZero(double rate) {
        assertThat(TradeService.experienceTax(100, rate)).isZero();
    }

    @Test
    @DisplayName("Control: 29% of 100 is exactly 29 (UltiTrade#64's exact floor is unchanged)")
    void twentyNinePercentOfAHundredIsTwentyNine() {
        assertThat(TradeService.experienceTax(100, 0.29)).isEqualTo(29);
        assertThat(TradeService.experienceTax(50, 0.29)).isEqualTo(14);
        assertThat(TradeService.experienceTax(0, 0.29)).isZero();
    }
}
