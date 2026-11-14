package com.foundation.sequence.domain;

import com.foundation.common.api.error.BizException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-09 BAS-18 §18.2：编号渲染与校验位。 */
class SequenceGeneratorTest {

    private static SequenceRule rule(String pattern, String prefix, int seqLength, CheckDigit checkDigit) {
        return new SequenceRule("sqr_1", "contract", "合同号", pattern, prefix, seqLength, 1,
                ResetCycle.NONE, checkDigit, SequenceStatus.ACTIVE, null, null, null);
    }

    @Test
    void rendersPrefixDateAndPaddedSequence() {
        SequenceRule rule = rule("{prefix}{date:yyyyMMdd}{seq:4}", "HT", 4, CheckDigit.NONE);

        String value = SequenceGenerator.render(rule, 7, LocalDate.of(2026, 2, 13));

        assertThat(value).isEqualTo("HT202602130007");
    }

    @Test
    void seqWithoutWidthFallsBackToRuleLength() {
        SequenceRule rule = rule("{prefix}-{seq}", "HT", 6, CheckDigit.NONE);

        assertThat(SequenceGenerator.render(rule, 12, LocalDate.of(2026, 2, 13))).isEqualTo("HT-000012");
    }

    @Test
    void sequenceExceedingWidthIsNotTruncated() {
        SequenceRule rule = rule("{seq:2}", null, 2, CheckDigit.NONE);

        assertThat(SequenceGenerator.render(rule, 12345, LocalDate.of(2026, 2, 13))).isEqualTo("12345");
    }

    @Test
    void checkDigitIsAppendedOverRenderedBody() {
        SequenceRule rule = rule("{prefix}{seq:4}{check}", "HT", 4, CheckDigit.MOD10);

        assertThat(SequenceGenerator.render(rule, 1, LocalDate.of(2026, 2, 13))).isEqualTo("HT00018");
    }

    @Test
    void sampleUsesSequenceOneAndTrimsNothing() {
        SequenceRule rule = rule("{prefix}{date:yyyy}{seq:3}", "C", 3, CheckDigit.NONE);

        assertThat(SequenceGenerator.sample(rule, LocalDate.of(2026, 2, 13))).isEqualTo("C2026001");
    }

    @Test
    void unknownPlaceholderIsRejected() {
        SequenceRule rule = rule("{prefix}{unknown}", "HT", 4, CheckDigit.NONE);

        assertThatThrownBy(() -> SequenceGenerator.render(rule, 1, LocalDate.of(2026, 2, 13)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未知编号占位符");
    }

    @Test
    void unclosedPlaceholderIsRejected() {
        SequenceRule rule = rule("{prefix}{seq:4", "HT", 4, CheckDigit.NONE);

        assertThatThrownBy(() -> SequenceGenerator.render(rule, 1, LocalDate.of(2026, 2, 13)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未闭合");
    }

    @Test
    void withSampleReturnsCopy() {
        SequenceRule base = new SequenceRule("sqr_1", "c", "n", "{seq}", null, 4, 1,
                ResetCycle.NONE, CheckDigit.NONE, SequenceStatus.ACTIVE, null, LocalDateTime.now(), null);

        SequenceRule sampled = base.withSample("0001");

        assertThat(sampled.sample()).isEqualTo("0001");
        assertThat(base.sample()).isNull();
        assertThat(sampled.id()).isEqualTo(base.id());
    }
}
