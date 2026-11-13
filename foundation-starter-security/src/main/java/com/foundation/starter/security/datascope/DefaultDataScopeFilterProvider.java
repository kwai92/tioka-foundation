package com.foundation.starter.security.datascope;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.starter.security.snapshot.AuthzSnapshot;
import com.foundation.starter.security.snapshot.DeptScope;
import com.foundation.starter.security.snapshot.DimensionScope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认数据范围条件生成器（F-07 §5）。
 *
 * <p>生成形如 {@code dept_code IN (:ds_dept_code_0, …) AND region_code IN (:ds_region_code_0, …)}
 * 的 SQL 片段与具名参数；部门与维度条件取**交集**（{@code AND}），与外部（Tioka 空间行级过滤）
 * 条件再由应用取交集拼入同一查询。
 */
public class DefaultDataScopeFilterProvider implements DataScopeFilterProvider {

    @Override
    public DataScopeFilter filter(String departmentColumn, String dimension,
                                  String dimensionColumn, AuthzSnapshot snapshot) {
        if (snapshot == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "数据范围不可用：授权快照缺失");
        }
        DeptScope deptScope = snapshot.deptScope();
        if (deptScope.all()) {
            // 「全部」→ 1=1，且不追加维度条件
            return DataScopeFilter.ofAll();
        }

        List<String> parts = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        appendIn(parts, params, departmentColumn, deptScope.values());

        if (dimension != null && !dimension.isBlank()
                && dimensionColumn != null && !dimensionColumn.isBlank()) {
            DimensionScope dimensionScope = snapshot.dimensions().get(dimension);
            if (dimensionScope == null) {
                // 未授予该维度 → 默认拒绝
                parts.add("1=0");
            } else if (!dimensionScope.all()) {
                appendIn(parts, params, dimensionColumn, dimensionScope.values());
            }
        }

        String sql = parts.isEmpty() ? "1=0" : String.join(" AND ", parts);
        return new DataScopeFilter(false, sql, params);
    }

    private static void appendIn(List<String> parts, Map<String, Object> params,
                                 String column, Collection<String> values) {
        if (values == null || values.isEmpty()) {
            // 空范围 → 无可见数据（默认拒绝）
            parts.add("1=0");
            return;
        }
        List<String> placeholders = new ArrayList<>(values.size());
        int index = 0;
        for (String value : values) {
            String name = "ds_" + column + "_" + index++;
            placeholders.add(":" + name);
            params.put(name, value);
        }
        parts.add(column + " IN (" + String.join(", ", placeholders) + ")");
    }
}
