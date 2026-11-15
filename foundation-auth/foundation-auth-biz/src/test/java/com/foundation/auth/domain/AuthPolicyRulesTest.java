package com.foundation.auth.domain;

import com.foundation.common.api.error.BizException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-04 BAS-15 §2：密码 / 会话策略校验与 TOTP 角色规范化。 */
class AuthPolicyRulesTest {

    @Test
    void acceptsValidPasswordPolicyAndTrims() {
        assertThat(AuthPolicyRules.requirePasswordPolicy(" length(8) and digits(1) and upperCase(1) "))
                .isEqualTo("length(8) and digits(1) and upperCase(1)");
        assertThat(AuthPolicyRules.requirePasswordPolicy("length(12)")).isEqualTo("length(12)");
    }

    @Test
    void rejectsBlankOrMalformedPasswordPolicy() {
        assertThatThrownBy(() -> AuthPolicyRules.requirePasswordPolicy("  "))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> AuthPolicyRules.requirePasswordPolicy("length 8"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> AuthPolicyRules.requirePasswordPolicy("length(8) or digits(1)"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void validatesSessionAndConcurrencyBounds() {
        assertThat(AuthPolicyRules.requireSessionIdleMinutes(30)).isEqualTo(30);
        assertThat(AuthPolicyRules.requireSessionMaxHours(10)).isEqualTo(10);
        assertThat(AuthPolicyRules.requireConcurrentSessions(0)).isZero();
        assertThat(AuthPolicyRules.requireSortOrder(5)).isEqualTo(5);

        assertThatThrownBy(() -> AuthPolicyRules.requireSessionIdleMinutes(0))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> AuthPolicyRules.requireSessionIdleMinutes(24 * 60 + 1))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> AuthPolicyRules.requireSessionMaxHours(0))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> AuthPolicyRules.requireConcurrentSessions(-1))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> AuthPolicyRules.requireSortOrder(-1))
                .isInstanceOf(BizException.class);
    }

    @Test
    void normalizesTotpRolesAndRoundTrips() {
        List<String> normalized = AuthPolicyRules.normalizeTotpRoles(
                Arrays.asList("security_admin", " security_admin ", null, "  ", "system_admin"));
        assertThat(normalized).containsExactly("security_admin", "system_admin");

        String csv = AuthPolicyRules.joinTotpRoles(normalized);
        assertThat(csv).isEqualTo("security_admin,system_admin");
        assertThat(AuthPolicyRules.parseTotpRoles(csv)).containsExactly("security_admin", "system_admin");
    }

    @Test
    void parsesEmptyTotpRolesAsEmptyList() {
        assertThat(AuthPolicyRules.parseTotpRoles(null)).isEmpty();
        assertThat(AuthPolicyRules.parseTotpRoles("  ")).isEmpty();
        assertThat(AuthPolicyRules.normalizeTotpRoles(null)).isEmpty();
    }
}
