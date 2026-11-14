package com.foundation.authz.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.util.List;
import java.util.Set;

/** 组织分级管理员授权规则（F-05 BAS-27 §分级授权）。 */
public final class OrgAdminScopeRule {

    private OrgAdminScopeRule() {
    }

    /** 范围是否覆盖目标组织单元：直接命中，或含下级且目标在子树内。 */
    public static boolean covers(OrgAdminScope scope, String targetOrgUnitId, Set<String> scopeSubtree) {
        if (scope.orgUnitId().equals(targetOrgUnitId)) {
            return true;
        }
        return scope.includeDescendants()
                && scopeSubtree != null
                && scopeSubtree.contains(targetOrgUnitId);
    }

    /** 能否授予某角色：范围覆盖目标，且（白名单为空 或 含该角色）。 */
    public static boolean canGrant(OrgAdminScope scope, String targetOrgUnitId,
                                   Set<String> scopeSubtree, String roleCode) {
        if (!covers(scope, targetOrgUnitId, scopeSubtree)) {
            return false;
        }
        return scope.grantableRoles().isEmpty() || scope.grantableRoles().contains(roleCode);
    }

    /** 校验角色可授予：任一范围满足即通过，否则抛 {@code FORBIDDEN}。 */
    public static void assertGrantable(List<OrgAdminScope> scopes, String targetOrgUnitId,
                                       Set<String> scopeSubtree, String roleCode) {
        if (scopes != null) {
            for (OrgAdminScope scope : scopes) {
                if (canGrant(scope, targetOrgUnitId, scopeSubtree, roleCode)) {
                    return;
                }
            }
        }
        throw new BizException(ErrorCode.FORBIDDEN,
                "越权授予：分级管理员不能授予范围外的角色 " + roleCode);
    }
}
