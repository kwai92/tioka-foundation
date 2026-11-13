package com.foundation.authz.api;

import java.util.List;
import java.util.Map;

/**
 * 角色数据范围视图（F-06 BAS-06 §接口）。
 *
 * @param roleCode   角色编码
 * @param deptScope  部门档位
 * @param deptUnits  {@code CUSTOM} 时的组织单元 ID 集合
 * @param dimensions 各维度取值，键为维度编码
 */
public record RoleDataScopeView(String roleCode,
                                String deptScope,
                                List<String> deptUnits,
                                Map<String, ScopeView> dimensions) {

    public RoleDataScopeView {
        deptUnits = deptUnits == null ? List.of() : List.copyOf(deptUnits);
        dimensions = dimensions == null ? Map.of() : Map.copyOf(dimensions);
    }
}
