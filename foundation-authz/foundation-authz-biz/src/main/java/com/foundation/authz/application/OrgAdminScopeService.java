package com.foundation.authz.application;

import com.foundation.authz.api.OrgAdminScopeApi;
import com.foundation.authz.api.OrgAdminScopeCmd;
import com.foundation.authz.api.OrgAdminScopeView;
import com.foundation.authz.domain.AuthzIdPrefix;
import com.foundation.authz.domain.OrgAdminScope;
import com.foundation.authz.domain.OrgAdminScopeRule;
import com.foundation.authz.domain.SubjectMembership;
import com.foundation.authz.infrastructure.OrgAdminScopeRepository;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 组织分级管理员范围（F-05 BAS-27 §分级授权）。
 *
 * <p>管理员只能看到并授予其被授权范围内的角色；越权授予由 {@link #assertGrantable} 拒绝（403）。
 */
public class OrgAdminScopeService implements OrgAdminScopeApi {

    private final OrgAdminScopeRepository scopes;
    private final SubjectMembership membership;

    public OrgAdminScopeService(OrgAdminScopeRepository scopes, SubjectMembership membership) {
        this.scopes = scopes;
        this.membership = membership;
    }

    @Override
    public List<OrgAdminScopeView> list(String userId) {
        return scopes.list(userId).stream().map(OrgAdminScopeService::toView).toList();
    }

    @Override
    @Transactional
    public OrgAdminScopeView upsert(OrgAdminScopeCmd cmd, String operator) {
        boolean includeDescendants = cmd.includeDescendants() == null || cmd.includeDescendants();
        List<String> grantableRoles = cmd.grantableRoles() == null ? List.of() : List.copyOf(cmd.grantableRoles());

        Optional<OrgAdminScope> existing = scopes.findByUserAndOrgUnit(cmd.userId(), cmd.orgUnitId());
        if (existing.isPresent()) {
            OrgAdminScope updated = new OrgAdminScope(existing.get().id(), cmd.userId(), cmd.orgUnitId(),
                    includeDescendants, grantableRoles);
            scopes.update(updated);
            return toView(updated);
        }
        OrgAdminScope created = new OrgAdminScope(Ids.newId(AuthzIdPrefix.ORG_ADMIN_SCOPE), cmd.userId(),
                cmd.orgUnitId(), includeDescendants, grantableRoles);
        scopes.insert(created);
        return toView(created);
    }

    /**
     * 校验分级管理员能否把某角色授予目标组织单元下的人员；越权抛 {@code FORBIDDEN}。
     *
     * <p>每个范围按其自身子树判定（{@code include_descendants} 时含下级）。
     */
    public void assertGrantable(String adminUserId, String targetOrgUnitId, String roleCode) {
        for (OrgAdminScope scope : scopes.list(adminUserId)) {
            if (OrgAdminScopeRule.canGrant(scope, targetOrgUnitId,
                    membership.descendantsOf(scope.orgUnitId()), roleCode)) {
                return;
            }
        }
        throw new BizException(ErrorCode.FORBIDDEN,
                "越权授予：分级管理员不能授予范围外的角色 " + roleCode);
    }

    private static OrgAdminScopeView toView(OrgAdminScope scope) {
        return new OrgAdminScopeView(scope.id(), scope.userId(), scope.orgUnitId(),
                scope.includeDescendants(), scope.grantableRoles());
    }
}
