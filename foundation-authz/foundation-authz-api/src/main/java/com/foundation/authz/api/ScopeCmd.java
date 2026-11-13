package com.foundation.authz.api;

import java.util.List;

/**
 * 范围入参（F-06 BAS-06 §接口）：{@code all = true} 表示「全部」，此时 {@code values} 为空。
 *
 * @param all    是否全部
 * @param values 取值集合（维度值编码或组织单元 ID）
 */
public record ScopeCmd(boolean all, List<String> values) {

    public ScopeCmd {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
