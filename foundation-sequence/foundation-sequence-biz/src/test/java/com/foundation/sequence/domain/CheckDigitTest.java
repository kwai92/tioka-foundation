package com.foundation.sequence.domain;

import com.foundation.common.api.error.BizException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-09 BAS-18 §18.2：校验位算法与重置周期枚举。 */
class CheckDigitTest {

    @Test
    void noneProducesEmptyCheck() {
        assertThat(CheckDigit.NONE.compute("HT0001")).isEmpty();
    }

    @Test
    void mod10FollowsLuhn() {
        assertThat(CheckDigit.MOD10.compute("HT0001")).isEqualTo("8");
        assertThat(CheckDigit.MOD10.compute("7992739871")).isEqualTo("3");
    }

    @Test
    void mod11UsesWeightsAndXForTen() {
        assertThat(CheckDigit.MOD11.compute("1")).isEqualTo("9");
        assertThat(CheckDigit.MOD11.compute("12")).isEqualTo("4");
        assertThat(CheckDigit.MOD11.compute("6")).isEqualTo("X");
    }

    @Test
    void digitsAreParsedCaseInsensitivelyAndStrictly() {
        assertThat(CheckDigit.of("mod10")).isEqualTo(CheckDigit.MOD10);
        assertThatThrownBy(() -> CheckDigit.of("SHA")).isInstanceOf(BizException.class);
    }

    @Test
    void resetCyclePeriodKeys() {
        LocalDate date = LocalDate.of(2026, 2, 13);
        assertThat(ResetCycle.NONE.periodKey(date)).isEqualTo("-");
        assertThat(ResetCycle.DAILY.periodKey(date)).isEqualTo("20260213");
        assertThat(ResetCycle.MONTHLY.periodKey(date)).isEqualTo("202602");
        assertThat(ResetCycle.YEARLY.periodKey(date)).isEqualTo("2026");
    }

    @Test
    void resetCycleIsParsedStrictly() {
        assertThat(ResetCycle.of("monthly")).isEqualTo(ResetCycle.MONTHLY);
        assertThatThrownBy(() -> ResetCycle.of("WEEKLY")).isInstanceOf(BizException.class);
    }

    @Test
    void statusReadsLenientlyButWritesStrictly() {
        assertThat(SequenceStatus.of("inactive")).isEqualTo(SequenceStatus.INACTIVE);
        assertThat(SequenceStatus.of("garbage")).isEqualTo(SequenceStatus.ACTIVE);
        assertThatThrownBy(() -> SequenceStatus.require("garbage")).isInstanceOf(BizException.class);
    }
}
