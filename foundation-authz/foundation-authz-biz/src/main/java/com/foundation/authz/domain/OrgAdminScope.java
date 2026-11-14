package com.foundation.authz.domain;

import java.util.List;
import java.util.Objects;

/**
 * 组织分级管理员范围（F-05 BAS-27 §分级授权）：限定某管理员可管辖的组织单元与可授予的角色。
 *
 * @param id                 范围 ID（{@code sco_{ulid}}）
 * @param userId             管理员用户 ID
 * @param orgUnitId          管辖组织单元 ID
 * @param includeDescendants 是否含下级组织单元
 * @param grantableRoles     可授予角色白名单；空表示不限
 */
public record OrgAdminScope(String id,
                            String userId,
                            String orgUnitId,
                            boolean includeDescendants,
                            List<String> grantableRoles) {

    public OrgAdminScope {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(orgUnitId, "orgUnitId");
        grantableRoles = grantableRoles == null ? List.of() : List.copyOf(grantableRoles);
    }
}
