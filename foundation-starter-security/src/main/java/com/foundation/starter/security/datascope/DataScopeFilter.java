package com.foundation.starter.security.datascope;

import java.util.Map;

/**
 * 数据范围过滤条件（F-07 §5）：生成器**返回 SQL 片段与参数**，不返回大集合。
 *
 * @param all    {@code true} 表示「全部」，此时 {@code sql} 为 {@code 1=1} 且 {@code params} 为空
 * @param sql    可直接拼入 MyBatis-Plus Wrapper 的 SQL 片段
 * @param params 具名参数
 */
public record DataScopeFilter(boolean all, String sql, Map<String, Object> params) {

    public DataScopeFilter {
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    /** 「全部」哨兵：{@code 1=1}。 */
    public static DataScopeFilter ofAll() {
        return new DataScopeFilter(true, "1=1", Map.of());
    }
}
