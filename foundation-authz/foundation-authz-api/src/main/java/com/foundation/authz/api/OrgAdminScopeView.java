package com.foundation.authz.api;

import java.util.List;

/**
 * 组织分级管理员范围视图（F-05 BAS-27 §分级授权）。
 *
 * @param id                 范围 ID（{@code sco_{ulid}}）
 * @param userId             管理员用户 ID
 * @param orgUnitId          管辖组织单元 ID
 * @param includeDescendants 是否含下级组织单元
 * @param grantableRoles     可授予角色白名单；空表示不限
 */
public record OrgAdminScopeView(String id,
                                String userId,
                                String orgUnitId,
                                boolean includeDescendants,
                                List<String> grantableRoles) {
}
