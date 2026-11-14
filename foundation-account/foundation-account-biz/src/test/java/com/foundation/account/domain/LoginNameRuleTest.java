package com.foundation.account.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-04 BAS-03 §8：登录名生成规则与冲突后缀。 */
class LoginNameRuleTest {

    @Test
    void defaultRuleUsesEmployeeNo() {
        assertThat(LoginNameRule.render(LoginNameRule.DEFAULT_RULE, "E1001")).isEqualTo("E1001");
        assertThat(LoginNameRule.render(null, "E1001")).isEqualTo("E1001");
        assertThat(LoginNameRule.render("  ", "E1001")).isEqualTo("E1001");
    }

    @Test
    void ruleTemplateRendersPlaceholder() {
        assertThat(LoginNameRule.render("{employeeNo}@tioka.com", "E1001")).isEqualTo("E1001@tioka.com");
        assertThat(LoginNameRule.render("t-{employeeNo}", "E1001")).isEqualTo("t-E1001");
    }

    @Test
    void employeeNoTrimmedAndRequired() {
        assertThat(LoginNameRule.render(LoginNameRule.DEFAULT_RULE, " E1001 ")).isEqualTo("E1001");
        assertThatThrownBy(() -> LoginNameRule.render(LoginNameRule.DEFAULT_RULE, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateReturnsBaseWhenFree() {
        assertThat(LoginNameRule.generate(LoginNameRule.DEFAULT_RULE, "E1001", name -> false))
                .isEqualTo("E1001");
    }

    @Test
    void generateAppendsConflictSuffix() {
        Set<String> taken = Set.of("E1001", "E1001-2");

        assertThat(LoginNameRule.generate(LoginNameRule.DEFAULT_RULE, "E1001", taken::contains))
                .isEqualTo("E1001-3");
    }

    @Test
    void isValidAcceptsAllowedCharactersOnly() {
        assertThat(LoginNameRule.isValid("ali.ce_1-a@tioka.com")).isTrue();
        assertThat(LoginNameRule.isValid("E1001")).isTrue();
        assertThat(LoginNameRule.isValid("张三")).isFalse();
        assertThat(LoginNameRule.isValid("")).isFalse();
        assertThat(LoginNameRule.isValid("a".repeat(129))).isFalse();
    }
}
