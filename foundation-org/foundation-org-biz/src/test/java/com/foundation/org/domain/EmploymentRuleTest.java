package com.foundation.org.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-03 BAS-02 §7.3：任职规则（一人一主任职、日期区间、切换计划）。 */
class EmploymentRuleTest {

    private final EmploymentRule rule = new EmploymentRule();

    private static Employment open(String id, boolean primary) {
        return new Employment(id, "per_1", "org_1", null, primary, LocalDate.of(2026, 1, 1), null);
    }

    private static Employment closed(String id, boolean primary) {
        return new Employment(id, "per_1", "org_1", null, primary, LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 12, 31));
    }

    @Test
    void startDateRequired() {
        assertThatThrownBy(() -> rule.assertDateRange(null, null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void endDateBeforeStartRejected() {
        assertThatThrownBy(() -> rule.assertDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2025, 12, 31)))
                .isInstanceOf(BizException.class);
    }

    @Test
    void equalDatesAndOpenEndAccepted() {
        rule.assertDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));
        rule.assertDateRange(LocalDate.of(2026, 1, 1), null);
    }

    @Test
    void newPrimaryRejectedWhenAnotherOpenPrimaryExists() {
        assertThatThrownBy(() -> rule.assertPrimaryAvailable(null, List.of(open("emp_1", true))))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void closedPrimaryDoesNotBlockNewPrimary() {
        rule.assertPrimaryAvailable(null, List.of(closed("emp_old", true)));
    }

    @Test
    void nonPrimaryOpenEmploymentDoesNotBlock() {
        rule.assertPrimaryAvailable(null, List.of(open("emp_1", false)));
    }

    @Test
    void excludeSelfAllowsKeepingOwnPrimary() {
        rule.assertPrimaryAvailable("emp_1", List.of(open("emp_1", true)));
    }

    @Test
    void switchPlanListsOtherOpenPrimaries() {
        List<String> plan = rule.primarySwitchPlan("emp_new",
                List.of(open("emp_old", true), open("emp_other", false), closed("emp_past", true)));

        assertThat(plan).containsExactly("emp_old");
    }

    @Test
    void switchPlanEmptyWhenNoOtherOpenPrimary() {
        assertThat(rule.primarySwitchPlan("emp_new", List.of(open("emp_other", false)))).isEmpty();
    }
}
