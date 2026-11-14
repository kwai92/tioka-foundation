package com.foundation.sync.scim.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * F-08 BAS-13 §3、§6：SCIM {@code filter} 子集（{@code eq} / {@code co} / {@code sw}）解析与匹配；
 * 不支持、复合或未知属性一律 {@code 400}（**不静默忽略**）。
 */
class ScimFilterTest {

    @Test
    void blankFilterIsAbsentAndMatchesEverything() {
        ScimFilter filter = ScimFilter.parse("  ");

        assertThat(filter.present()).isFalse();
        assertThat(filter.matches("anything")).isTrue();
    }

    @Test
    void parsesEqOperator() {
        ScimFilter filter = ScimFilter.parse("userName eq \"alice\"");

        assertThat(filter.attribute()).isEqualTo("userName");
        assertThat(filter.operator()).isEqualTo(ScimFilterOperator.EQ);
        assertThat(filter.value()).isEqualTo("alice");
    }

    @Test
    void eqMatchesCaseInsensitively() {
        ScimFilter filter = ScimFilter.parse("userName eq \"Alice\"");

        assertThat(filter.matches("alice")).isTrue();
        assertThat(filter.matches("bob")).isFalse();
    }

    @Test
    void coMatchesSubstring() {
        ScimFilter filter = ScimFilter.parse("userName co \"ali\"");

        assertThat(filter.matches("Alice")).isTrue();
        assertThat(filter.matches("bob")).isFalse();
    }

    @Test
    void swMatchesPrefix() {
        ScimFilter filter = ScimFilter.parse("userName sw \"Al\"");

        assertThat(filter.matches("alice")).isTrue();
        assertThat(filter.matches("malice")).isFalse();
    }

    @Test
    void nullAttributeValueNeverMatches() {
        assertThat(ScimFilter.parse("displayName eq \"x\"").matches(null)).isFalse();
    }

    @Test
    void unsupportedOperatorIsRejected() {
        assertThatThrownBy(() -> ScimFilter.parse("userName gt \"alice\""))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void compoundFilterIsRejected() {
        assertThatThrownBy(() -> ScimFilter.parse("userName eq \"a\" and viewId eq \"b\""))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void unknownAttributeIsRejected() {
        assertThatThrownBy(() -> ScimFilter.parse("nickName eq \"x\"", ScimConstants.USER_FILTER_ATTRIBUTES))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void allowedAttributePassesWhitelist() {
        ScimFilter filter = ScimFilter.parse("externalId eq \"e1\"", ScimConstants.USER_FILTER_ATTRIBUTES);

        assertThat(filter.present()).isTrue();
        assertThat(filter.attribute()).isEqualTo("externalId");
    }
}
