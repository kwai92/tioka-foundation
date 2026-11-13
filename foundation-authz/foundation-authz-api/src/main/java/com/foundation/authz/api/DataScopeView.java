package com.foundation.authz.api;

import java.util.List;
import java.util.Map;

/**
 * 数据范围解析结果视图（F-06 BAS-06 §合并规则；供 BAS-07 快照与「谁有这个权限」反查）。
 *
 * @param deptScope  部门范围（{@code values} 为展开后的组织单元 ID 集合）
 * @param dimensions 各维度取值，键为维度编码
 */
public record DataScopeView(ScopeView deptScope, Map<String, ScopeView> dimensions) {

    public DataScopeView {
        deptScope = deptScope == null ? ScopeView.of(List.of()) : deptScope;
        dimensions = dimensions == null ? Map.of() : Map.copyOf(dimensions);
    }
}
