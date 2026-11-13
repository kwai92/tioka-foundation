package com.foundation.authz.api;

import java.util.List;
import java.util.Map;

/**
 * 设置角色数据范围入参（F-06 BAS-06 §接口 {@code PUT /api/fdn/roles/{roleCode}/data-scope}）。
 *
 * @param deptScope  {@code ALL} / {@code CUSTOM} / {@code DEPT} / {@code DEPT_AND_SUB} / {@code SELF}
 * @param deptUnits  {@code CUSTOM} 时的组织单元 ID 集合
 * @param dimensions 各维度取值，键为维度编码
 */
public record RoleDataScopeCmd(String deptScope,
                               List<String> deptUnits,
                               Map<String, ScopeCmd> dimensions) {

    public RoleDataScopeCmd {
        deptUnits = deptUnits == null ? List.of() : List.copyOf(deptUnits);
        dimensions = dimensions == null ? Map.of() : Map.copyOf(dimensions);
    }
}
