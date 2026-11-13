package com.foundation.org.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-03 BAS-02 §7.3：组织树规则（树深、防环、停用前置、物化路径）。 */
class OrgTreePolicyTest {

    private final OrgTreePolicy policy = new OrgTreePolicy(10);

    @Test
    void rootPathAndChildPathUseSlashSeparator() {
        assertThat(policy.rootPath("org_a")).isEqualTo("/org_a");
        assertThat(policy.childPath("/org_a", "org_b")).isEqualTo("/org_a/org_b");
    }

    @Test
    void depthOfPathCountsSegments() {
        assertThat(policy.depthOfPath(null)).isZero();
        assertThat(policy.depthOfPath("")).isZero();
        assertThat(policy.depthOfPath("/org_a")).isEqualTo(1);
        assertThat(policy.depthOfPath("/org_a/org_b/org_c")).isEqualTo(3);
    }

    @Test
    void depthOfChildIsOneWhenNoParent() {
        assertThat(policy.depthOfChild(null)).isEqualTo(1);
        assertThat(policy.depthOfChild("/org_a")).isEqualTo(2);
    }

    @Test
    void depthWithinLimitAcceptedAndEleventhRejected() {
        policy.assertDepthWithinLimit(1);
        policy.assertDepthWithinLimit(10);

        assertThatThrownBy(() -> policy.assertDepthWithinLimit(11))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void zeroOrNegativeDepthRejected() {
        assertThatThrownBy(() -> policy.assertDepthWithinLimit(0)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> policy.assertDepthWithinLimit(-1)).isInstanceOf(BizException.class);
    }

    @Test
    void movingUnderSelfRejected() {
        assertThatThrownBy(() -> policy.assertNoCycle("org_a", "org_a", Set.of("org_a")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void movingUnderOwnDescendantRejected() {
        assertThatThrownBy(() -> policy.assertNoCycle("org_a", "org_child", Set.of("org_a", "org_child")))
                .isInstanceOf(BizException.class);
    }

    @Test
    void movingToUnrelatedParentAccepted() {
        policy.assertNoCycle("org_a", "org_other", Set.of("org_a", "org_child"));
    }

    @Test
    void blankParentMeansRootAndAccepted() {
        policy.assertNoCycle("org_a", null, Set.of("org_a"));
        policy.assertNoCycle("org_a", "", Set.of("org_a"));
    }

    @Test
    void deactivateRejectedWhenHasChildren() {
        assertThatThrownBy(() -> policy.assertDeactivatable(true, 0))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void deactivateRejectedWhenActiveEmploymentExists() {
        assertThatThrownBy(() -> policy.assertDeactivatable(false, 3))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void deactivateAcceptedWhenLeafAndEmpty() {
        policy.assertDeactivatable(false, 0);
    }

    @Test
    void rewritePathReplacesPrefix() {
        assertThat(policy.rewritePath("/org_a/org_b/org_c", "/org_a", "/org_x"))
                .isEqualTo("/org_x/org_b/org_c");
        assertThat(policy.rewritePath("/org_a", "/org_a", "/org_x")).isEqualTo("/org_x");
    }

    @Test
    void rewritePathRejectsPathOutsideSubtree() {
        assertThatThrownBy(() -> policy.rewritePath("/org_other", "/org_a", "/org_x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maxDepthMustBePositive() {
        assertThatThrownBy(() -> new OrgTreePolicy(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
