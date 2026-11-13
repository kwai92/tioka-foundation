package com.foundation.org.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.org.api.dto.PersonStatus;
import com.foundation.org.api.dto.PersonType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-03 BAS-02 §3、§7.3：数字员工规则（负责人必填、无账号、必挂主任职、下岗置停用）。 */
class DigitalEmployeeRuleTest {

    private final DigitalEmployeeRule rule = new DigitalEmployeeRule();

    @Test
    void ownerRequired() {
        assertThatThrownBy(() -> rule.assertOwnerProvided(null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> rule.assertOwnerProvided("  ")).isInstanceOf(BizException.class);
    }

    @Test
    void ownerPresentAccepted() {
        rule.assertOwnerProvided("per_owner");
    }

    @Test
    void primaryEmploymentRequired() {
        rule.assertPrimaryEmployment(true);
        assertThatThrownBy(() -> rule.assertPrimaryEmployment(false))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void digitalEmployeeCannotProvisionAccount() {
        assertThat(rule.accountProvisioningAllowed(PersonType.DIGITAL_EMPLOYEE)).isFalse();
        assertThat(rule.accountProvisioningAllowed(PersonType.EMPLOYEE)).isTrue();
        assertThat(rule.accountProvisioningAllowed(PersonType.OUTSOURCED)).isTrue();
    }

    @Test
    void offboardingSetsDisabled() {
        assertThat(rule.statusOnOffboarding()).isEqualTo(PersonStatus.DISABLED);
    }
}
