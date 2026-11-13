package com.foundation.starter.security.snapshot;

import java.util.Set;

/**
 * 自定义维度范围（F-07 §5）。
 *
 * @param all    {@code true} 表示「全部」，此时 {@code values} 为空
 * @param values 维度取值编码集合
 */
public record DimensionScope(boolean all, Set<String> values) {

    public DimensionScope {
        values = values == null ? Set.of() : Set.copyOf(values);
    }

    /** 「全部」哨兵。 */
    public static DimensionScope ofAll() {
        return new DimensionScope(true, Set.of());
    }
}
