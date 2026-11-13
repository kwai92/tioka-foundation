package com.foundation.authz.api;

import java.util.List;

/**
 * 人员维度授予入参（F-06 BAS-06 §接口 {@code POST /api/fdn/persons/{personId}/dimensions}）。
 *
 * @param dimensionCode 维度编码
 * @param all           是否「全部」
 * @param values        取值编码集合（{@code all} 时忽略）
 * @param source        {@code MANUAL} / {@code RULE} / {@code SYNC}；{@code RULE}/{@code SYNC} 不覆盖 {@code MANUAL}
 */
public record PersonDimensionCmd(String dimensionCode, boolean all, List<String> values, String source) {

    public PersonDimensionCmd {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
