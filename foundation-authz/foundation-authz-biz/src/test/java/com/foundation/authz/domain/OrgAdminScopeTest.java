package com.foundation.authz.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 组织分级管理员授权范围（F-05 BAS-27 §分级授权）。 */
class OrgAdminScopeTest {

    private static final Set<String> SUBTREE = Set.of("org_a", "org_b", "org_c");

    private static OrgAdminScope scope(String orgUnitId, boolean includeDescendants, List<String> roles) {
        return new OrgAdminScope("sco_1", "user_1", orgUnitId, includeDescendants, roles);
    }

    @Test
    void inScopeWithRoleInWhitelistCanGrant() {
        OrgAdminScope scope = scope("org_a", true, List.of("r.hr"));

        assertThat(OrgAdminScopeRule.canGrant(scope, "org_a", SUBTREE, "r.hr")).isTrue();
        OrgAdminScopeRule.assertGrantable(List.of(scope), "org_a", SUBTREE, "r.hr");
    }

    @Test
    void roleNotInWhitelistIsForbidden() {
        OrgAdminScope scope = scope("org_a", true, List.of("r.hr"));

        assertThatThrownBy(() -> OrgAdminScopeRule.assertGrantable(List.of(scope), "org_a", SUBTREE, "r.finance"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void targetOutOfScopeIsForbidden() {
        OrgAdminScope scope = scope("org_a", true, List.of());

        assertThatThrownBy(() -> OrgAdminScopeRule.assertGrantable(List.of(scope), "org_z", SUBTREE, "r.hr"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void descendantsNotCoveredWhenFlagFalse() {
        OrgAdminScope scope = scope("org_a", false, List.of());

        assertThat(OrgAdminScopeRule.covers(scope, "org_b", SUBTREE)).isFalse();
        assertThatThrownBy(() -> OrgAdminScopeRule.assertGrantable(List.of(scope), "org_b", SUBTREE, "r.hr"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void descendantsCoveredWhenFlagTrue() {
        OrgAdminScope scope = scope("org_a", true, List.of());

        assertThat(OrgAdminScopeRule.covers(scope, "org_b", SUBTREE)).isTrue();
    }

    @Test
    void emptyWhitelistMeansUnrestricted() {
        OrgAdminScope scope = scope("org_a", true, List.of());

        assertThat(OrgAdminScopeRule.canGrant(scope, "org_a", SUBTREE, "r.anything")).isTrue();
        OrgAdminScopeRule.assertGrantable(List.of(scope), "org_a", SUBTREE, "r.anything");
    }

    @Test
    void nullWhitelistTreatedAsEmpty() {
        OrgAdminScope scope = scope("org_a", true, null);

        assertThat(scope.grantableRoles()).isEmpty();
        assertThat(OrgAdminScopeRule.canGrant(scope, "org_a", SUBTREE, "r.anything")).isTrue();
    }
}
