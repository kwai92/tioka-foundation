package com.foundation.authz.domain;

import java.util.List;
import java.util.Objects;

/**
 * 角色数据范围（F-06 BAS-06 §数据模型 {@code fdn_role_data_scope}）。
 *
 * <p>部门范围是角色级的单值（五档之一）；{@link DeptScope#CUSTOM} 时 {@code deptUnits} 给出组织单元 ID 集合。
 *
 * @param id        行 ID（{@code frs_{ulid}}）
 * @param roleCode  角色编码
 * @param deptScope 部门档位，见 {@link DeptScope}
 * @param deptUnits 自定义部门单元 ID 集合（仅 {@link DeptScope#CUSTOM} 有意义）
 */
public record RoleDataScope(String id,
                            String roleCode,
                            DeptScope deptScope,
                            List<String> deptUnits) {

    public RoleDataScope {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(roleCode, "roleCode");
        Objects.requireNonNull(deptScope, "deptScope");
        deptUnits = deptUnits == null ? List.of() : List.copyOf(deptUnits);
    }
}
