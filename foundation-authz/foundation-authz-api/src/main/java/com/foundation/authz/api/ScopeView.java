package com.foundation.authz.api;

import java.util.List;

/**
 * 范围视图（F-06 BAS-06 §接口）：{@code all = true} 表示「全部」，此时 {@code values} 为空。
 *
 * @param all    是否全部
 * @param values 取值集合（维度值编码或组织单元 ID）
 */
public record ScopeView(boolean all, List<String> values) {

    public ScopeView {
        values = values == null ? List.of() : List.copyOf(values);
    }

    /** 由取值集合构造。 */
    public static ScopeView of(java.util.Collection<String> values) {
        return new ScopeView(false, List.copyOf(values));
    }

    /** 「全部」哨兵。 */
    public static ScopeView ofAll() {
        return new ScopeView(true, List.of());
    }
}
