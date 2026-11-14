package com.foundation.authz.domain;

import java.util.Objects;

/**
 * 功能开关（F-05 BAS-27 §功能开关）。
 *
 * @param id        开关 ID（{@code tog_{ulid}}）
 * @param scopeType 作用域类型，见 {@link ToggleScopeType}
 * @param scopeId   作用域 ID
 * @param enabled   是否启用
 * @param reason    原因说明，可空
 */
public record FeatureToggle(String id,
                            ToggleScopeType scopeType,
                            String scopeId,
                            boolean enabled,
                            String reason) {

    public FeatureToggle {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(scopeType, "scopeType");
        Objects.requireNonNull(scopeId, "scopeId");
    }

    /** 副本：变更开关状态与原因。 */
    public FeatureToggle withEnabled(boolean newEnabled, String newReason) {
        return new FeatureToggle(id, scopeType, scopeId, newEnabled, newReason);
    }
}
