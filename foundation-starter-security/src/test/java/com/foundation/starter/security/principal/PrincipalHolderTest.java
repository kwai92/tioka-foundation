package com.foundation.starter.security.principal;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** 当前人员承载（F-07 §2、§15）：{@code runAs} 恢复身份与版本；请求边界清空 ThreadLocal。 */
class PrincipalHolderTest {

    @AfterEach
    void tearDown() {
        PrincipalHolder.clear();
    }

    @Test
    void emptyWhenUnset() {
        assertThat(PrincipalHolder.current()).isEmpty();
        assertThat(PrincipalHolder.snapshotVersion()).isEmpty();
    }

    @Test
    void requireThrowsUnauthorized() {
        Throwable thrown = catchThrowable(PrincipalHolder::require);

        assertThat(thrown).isInstanceOf(BizException.class);
        assertThat(((BizException) thrown).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void setExposesPrincipalAndSnapshotVersion() {
        PrincipalHolder.set(principal(), 12L);

        assertThat(PrincipalHolder.current()).contains(principal());
        assertThat(PrincipalHolder.snapshotVersion()).hasValue(12L);
    }

    @Test
    void clearRemovesThreadLocal() {
        PrincipalHolder.set(principal(), 12L);

        PrincipalHolder.clear();

        assertThat(PrincipalHolder.current()).isEmpty();
        assertThat(PrincipalHolder.snapshotVersion()).isEmpty();
    }

    @Test
    void runAsRestoresPreviousIdentity() {
        PrincipalHolder.set(principal(), 1L);

        String result = PrincipalHolder.runAs(other(), 9L, () -> {
            assertThat(PrincipalHolder.current()).contains(other());
            assertThat(PrincipalHolder.snapshotVersion()).hasValue(9L);
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(PrincipalHolder.current()).contains(principal());
        assertThat(PrincipalHolder.snapshotVersion()).hasValue(1L);
    }

    @Test
    void runAsRestoresEmptyWhenNoPreviousIdentity() {
        PrincipalHolder.runAs(other(), 3L, () -> null);

        assertThat(PrincipalHolder.current()).isEmpty();
        assertThat(PrincipalHolder.snapshotVersion()).isEmpty();
    }

    private static Principal principal() {
        return new Principal("1", "per_1", "acc_1", "E1001", "张三", "ou_root", "tioka");
    }

    private static Principal other() {
        return new Principal("2", "per_2", "acc_2", "E1002", "李四", "ou_child", "tioka");
    }
}
