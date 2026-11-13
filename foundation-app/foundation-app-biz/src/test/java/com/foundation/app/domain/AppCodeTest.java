package com.foundation.app.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-05 BAS-04：应用编码正则 {@code ^[a-z][a-z0-9-]{1,31}$}、登记后不可改。 */
class AppCodeTest {

    @Test
    void acceptsLowercaseDigitsAndHyphen() {
        assertThat(AppCode.of("order-center").value()).isEqualTo("order-center");
        assertThat(AppCode.of("order2").value()).isEqualTo("order2");
        assertThat(AppCode.of("ab").value()).isEqualTo("ab");
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(AppCode.of("  order-center  ").value()).isEqualTo("order-center");
    }

    @Test
    void acceptsMaxLengthOf32() {
        String maxLength = "a" + "b".repeat(31);
        assertThat(maxLength).hasSize(32);
        assertThat(AppCode.of(maxLength).value()).isEqualTo(maxLength);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "A", "2order", "order_service", "order.center", "订单"})
    void rejectsViolatingPattern(String raw) {
        assertThatThrownBy(() -> AppCode.of(raw))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void rejectsTooLongCode() {
        assertThatThrownBy(() -> AppCode.of("a" + "b".repeat(32)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void rejectsBlankOrNull() {
        assertThatThrownBy(() -> AppCode.of(null)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> AppCode.of("   ")).isInstanceOf(BizException.class);
    }

    @Test
    void updateProfileKeepsCodeUnchanged() {
        App app = App.register("app_1", "order-center", "订单中心", null, null, AppKind.SUB, "u1");

        App updated = app.updateProfile("订单中心（新）", "icon", "https://order.example.com", "u2",
                App.STATUS_DISABLED);

        assertThat(updated.code()).isEqualTo("order-center");
        assertThat(updated.name()).isEqualTo("订单中心（新）");
        assertThat(updated.status()).isEqualTo(App.STATUS_DISABLED);
        assertThat(updated.manifestVersion()).isZero();
    }
}
