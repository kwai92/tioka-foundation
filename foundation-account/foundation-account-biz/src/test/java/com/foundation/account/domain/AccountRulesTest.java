package com.foundation.account.domain;

import com.foundation.account.api.dto.AccountStatus;
import com.foundation.account.api.dto.AccountType;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-04 BAS-03 §1：账号规则（人员绑定、一人一账号、初始状态与状态迁移）。 */
class AccountRulesTest {

    private static ErrorCode codeOf(Throwable ex) {
        return ((BizException) ex).getErrorCode();
    }

    @Test
    void personalRequiresPersonBinding() {
        assertThatThrownBy(() -> AccountRules.requirePersonBinding(AccountType.PERSONAL, null))
                .isInstanceOf(BizException.class)
                .extracting(AccountRulesTest::codeOf)
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatCode(() -> AccountRules.requirePersonBinding(AccountType.PERSONAL, "per_1"))
                .doesNotThrowAnyException();
    }

    @Test
    void emergencyAdminMustNotBindPerson() {
        assertThatThrownBy(() -> AccountRules.requirePersonBinding(AccountType.EMERGENCY_ADMIN, "per_1"))
                .isInstanceOf(BizException.class)
                .extracting(AccountRulesTest::codeOf)
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatCode(() -> AccountRules.requirePersonBinding(AccountType.EMERGENCY_ADMIN, null))
                .doesNotThrowAnyException();
    }

    @Test
    void serviceAccountMustNotBindPerson() {
        assertThatThrownBy(() -> AccountRules.requirePersonBinding(AccountType.SERVICE, "per_1"))
                .isInstanceOf(BizException.class)
                .extracting(AccountRulesTest::codeOf)
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatCode(() -> AccountRules.requirePersonBinding(AccountType.SERVICE, null))
                .doesNotThrowAnyException();
    }

    @Test
    void typeRequired() {
        assertThatThrownBy(() -> AccountRules.requirePersonBinding(null, null))
                .isInstanceOf(BizException.class)
                .extracting(AccountRulesTest::codeOf)
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void secondPersonalAccountRejected() {
        assertThatThrownBy(() -> AccountRules.requireNoExistingPersonal(true))
                .isInstanceOf(BizException.class)
                .extracting(AccountRulesTest::codeOf)
                .isEqualTo(ErrorCode.CONFLICT);
        assertThatCode(() -> AccountRules.requireNoExistingPersonal(false)).doesNotThrowAnyException();
    }

    @Test
    void initialStatusByType() {
        assertThat(AccountRules.initialStatus(AccountType.PERSONAL)).isEqualTo(AccountStatus.NORMAL);
        assertThat(AccountRules.initialStatus(AccountType.SERVICE)).isEqualTo(AccountStatus.NORMAL);
        assertThat(AccountRules.initialStatus(AccountType.EMERGENCY_ADMIN)).isEqualTo(AccountStatus.DISABLED);
    }

    @Test
    void statusTransitions() {
        assertThat(AccountRules.canDisable(AccountStatus.NORMAL)).isTrue();
        assertThat(AccountRules.canDisable(AccountStatus.LOCKED)).isTrue();
        assertThat(AccountRules.canDisable(AccountStatus.DISABLED)).isFalse();

        assertThat(AccountRules.canLock(AccountStatus.NORMAL)).isTrue();
        assertThat(AccountRules.canLock(AccountStatus.LOCKED)).isFalse();
        assertThat(AccountRules.canLock(AccountStatus.DISABLED)).isFalse();

        assertThat(AccountRules.canUnlock(AccountStatus.LOCKED)).isTrue();
        assertThat(AccountRules.canUnlock(AccountStatus.NORMAL)).isFalse();
    }

    @Test
    void loginNameMustBeValid() {
        assertThatThrownBy(() -> AccountRules.requireLoginName("  "))
                .isInstanceOf(BizException.class)
                .extracting(AccountRulesTest::codeOf)
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> AccountRules.requireLoginName("张三"))
                .isInstanceOf(BizException.class)
                .extracting(AccountRulesTest::codeOf)
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatCode(() -> AccountRules.requireLoginName("E1001")).doesNotThrowAnyException();
    }
}
