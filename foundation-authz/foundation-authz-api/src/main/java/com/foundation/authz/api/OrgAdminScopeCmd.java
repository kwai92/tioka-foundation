package com.foundation.authz.api;

import java.util.List;

/**
 * 组织分级管理员范围命令（F-05 BAS-27 §分级授权）。
 *
 * @param userId             管理员用户 ID
 * @param orgUnitId          管辖组织单元 ID
 * @param includeDescendants 是否含下级组织单元，{@code null} 表示默认
 * @param grantableRoles     可授予角色白名单；空表示不限
 */
public record OrgAdminScopeCmd(String userId,
                               String orgUnitId,
                               Boolean includeDescendants,
                               List<String> grantableRoles) {
}
