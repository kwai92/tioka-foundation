package com.foundation.authz.api;

import java.util.List;

/**
 * 功能开关契约（F-05 BAS-27 §功能开关）。
 *
 * <p>供应用侧 starter（BAS-08）在入口校验时查询：{@link #disabled(String, String)} 沿「自身 → 上级」
 * 作用域链取第一个显式配置，最具体作用域优先（子作用域显式启用可覆盖父作用域停用）；链上无显式配置时不停用。
 */
public interface FeatureToggleApi {

    /** 开关列表。 */
    List<FeatureToggleView> list();

    /**
     * 设置某作用域的开关（覆盖式）。
     *
     * @param scopeType 作用域类型：{@code SUBSYSTEM} / {@code MODULE} / {@code FUNCTION}
     * @param scopeId   作用域 ID
     * @param enabled   是否启用
     * @param reason    原因说明，可空
     * @param operator  操作人
     */
    FeatureToggleView set(String scopeType, String scopeId, boolean enabled, String reason, String operator);

    /** 该作用域是否停用（沿作用域链继承）。 */
    boolean disabled(String scopeType, String scopeId);
}
