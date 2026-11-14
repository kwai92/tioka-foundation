package com.foundation.authz.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 授权模式切换（F-05 BAS-27 §模式切换）。 */
class AuthModeTransitionTest {

    @Test
    void selfToConcentratedRequiresImport() {
        assertThat(AuthModeRules.requiresSelfImport(AuthMode.SELF, AuthMode.CONCENTRATED)).isTrue();

        assertThatThrownBy(() -> AuthModeRules.assertTransition(AuthMode.SELF, AuthMode.CONCENTRATED, false))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void selfToConcentratedPassesWhenImportApplied() {
        AuthModeRules.assertTransition(AuthMode.SELF, AuthMode.CONCENTRATED, true);
    }

    @Test
    void concentratedToSelfNeedsNoImport() {
        assertThat(AuthModeRules.requiresSelfImport(AuthMode.CONCENTRATED, AuthMode.SELF)).isFalse();

        AuthModeRules.assertTransition(AuthMode.CONCENTRATED, AuthMode.SELF, false);
    }

    @Test
    void otherTransitionsNeedNoImport() {
        assertThat(AuthModeRules.requiresSelfImport(AuthMode.SELF, AuthMode.GRADED)).isFalse();
        assertThat(AuthModeRules.requiresSelfImport(AuthMode.HYBRID, AuthMode.CONCENTRATED)).isFalse();
    }

    @Test
    void ofRejectsIllegalValue() {
        assertThatThrownBy(() -> AuthMode.of("bogus"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void ofParsesCaseInsensitively() {
        assertThat(AuthMode.of("self")).isEqualTo(AuthMode.SELF);
    }
}
