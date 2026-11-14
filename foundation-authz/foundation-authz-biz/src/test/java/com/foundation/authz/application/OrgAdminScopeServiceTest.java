package com.foundation.authz.application;

import com.foundation.authz.api.OrgAdminScopeCmd;
import com.foundation.authz.api.OrgAdminScopeView;
import com.foundation.authz.domain.OrgAdminScope;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 组织分级管理员范围（F-05 BAS-27 §分级授权）。 */
class OrgAdminScopeServiceTest {

    private static final String ADMIN = "user_admin";

    private final TestDoubles.InMemoryOrgAdminScopeRepository scopes =
            new TestDoubles.InMemoryOrgAdminScopeRepository();
    private final TestDoubles.StubSubjectMembership membership = new TestDoubles.StubSubjectMembership();

    private OrgAdminScopeService service;

    @BeforeEach
    void setUp() {
        membership.descendants.put("org_a", Set.of("org_a", "org_b"));
        service = new OrgAdminScopeService(scopes, membership);
    }

    @Test
    void upsertInsertsThenUpdates() {
        OrgAdminScopeView created = service.upsert(new OrgAdminScopeCmd(ADMIN, "org_a", null,
                List.of("r.hr")), "admin");
        assertThat(created.includeDescendants()).isTrue();
        assertThat(created.grantableRoles()).containsExactly("r.hr");

        OrgAdminScopeView updated = service.upsert(new OrgAdminScopeCmd(ADMIN, "org_a", false,
                List.of("r.hr", "r.finance")), "admin");

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.includeDescendants()).isFalse();
        assertThat(scopes.list(ADMIN)).hasSize(1);
    }

    @Test
    void assertGrantableAllowsWithinScopeSubtree() {
        scopes.insert(new OrgAdminScope("sco_1", ADMIN, "org_a", true, List.of("r.hr")));

        assertThatCode(() -> service.assertGrantable(ADMIN, "org_a", "r.hr")).doesNotThrowAnyException();
        assertThatCode(() -> service.assertGrantable(ADMIN, "org_b", "r.hr")).doesNotThrowAnyException();
    }

    @Test
    void assertGrantableDeniesRoleOutsideWhitelist() {
        scopes.insert(new OrgAdminScope("sco_1", ADMIN, "org_a", true, List.of("r.hr")));

        assertThatThrownBy(() -> service.assertGrantable(ADMIN, "org_a", "r.finance"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void assertGrantableDeniesOrgUnitOutsideScope() {
        scopes.insert(new OrgAdminScope("sco_1", ADMIN, "org_a", false, List.of("r.hr")));

        assertThatThrownBy(() -> service.assertGrantable(ADMIN, "org_b", "r.hr"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void listReturnsAllScopesWhenUserAbsent() {
        scopes.insert(new OrgAdminScope("sco_1", ADMIN, "org_a", true, List.of()));
        scopes.insert(new OrgAdminScope("sco_2", "user_other", "org_c", true, List.of()));

        assertThat(service.list(null)).hasSize(2);
        assertThat(service.list(ADMIN)).hasSize(1);
    }
}
