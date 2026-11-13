package com.foundation.authz.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 数据范围计算结果（F-06 BAS-06 §合并规则、BAS-07 快照内容）。
 *
 * <p>部门范围以「展开后的组织单元 ID 集合或全部」表达；每个维度以「最终取值集合或全部」表达。
 * {@code all = true} 为哨兵，表示「全部」，此时 {@code values} 为空且不被并集抹掉。
 *
 * @param deptScope  部门范围
 * @param dimensions 各维度取值集合，键为维度编码
 */
public record DataScopeResolution(Scope deptScope, Map<String, Scope> dimensions) {

    public DataScopeResolution {
        deptScope = deptScope == null ? Scope.of(Set.of()) : deptScope;
        dimensions = dimensions == null ? Map.of() : Map.copyOf(dimensions);
    }

    /**
     * 单条范围。
     *
     * @param all    是否「全部」
     * @param values 编码集合（{@code all} 时为空）
     */
    public record Scope(boolean all, Set<String> values) {

        public Scope {
            values = values == null ? Set.of() : Set.copyOf(values);
        }

        /** 「全部」哨兵。 */
        public static Scope ofAll() {
            return new Scope(true, Set.of());
        }

        /** 由编码集合构造。 */
        public static Scope of(Collection<String> values) {
            return new Scope(false, Set.copyOf(values));
        }
    }
}
