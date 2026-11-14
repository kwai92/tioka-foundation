package com.foundation.authz.domain;

import java.util.List;
import java.util.Map;

/** 功能开关继承规则（F-05 BAS-27 §功能开关）。 */
public final class FeatureToggleRules {

    private FeatureToggleRules() {
    }

    /** 开关作用域键。 */
    public record ScopeKey(ToggleScopeType type, String scopeId) {
    }

    /**
     * 是否停用：沿「自身 → 上级 …」的作用域链取第一个显式配置，最具体作用域优先
     * （子作用域显式启用可覆盖父作用域停用）；链上无显式配置时不停用。
     */
    public static boolean disabled(List<ScopeKey> selfFirstChain, Map<ScopeKey, Boolean> toggles) {
        if (selfFirstChain == null) {
            return false;
        }
        for (ScopeKey key : selfFirstChain) {
            Boolean enabled = toggles == null ? null : toggles.get(key);
            if (enabled != null) {
                return !enabled;
            }
        }
        return false;
    }
}
