package com.foundation.starter.security.snapshot;

import java.util.Set;

/**
 * 部门范围（F-07 §5；部门五档 {@code ALL} / {@code CUSTOM} / {@code DEPT} / {@code DEPT_AND_SUB} / {@code SELF}
 * 在快照里已归约为「全部」或「组织单元 ID 集合」）。
 *
 * @param all    {@code true} 表示「全部」，此时 {@code values} 为空
 * @param values 展开后的组织单元 ID 集合
 */
public record DeptScope(boolean all, Set<String> values) {

    public DeptScope {
        values = values == null ? Set.of() : Set.copyOf(values);
    }

    /** 「全部」哨兵。 */
    public static DeptScope ofAll() {
        return new DeptScope(true, Set.of());
    }
}
