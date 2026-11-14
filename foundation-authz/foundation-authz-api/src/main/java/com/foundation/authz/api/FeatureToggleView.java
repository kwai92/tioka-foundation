package com.foundation.authz.api;

/**
 * 功能开关视图（F-05 BAS-27 §功能开关）。
 *
 * @param scopeType 作用域类型：{@code SUBSYSTEM} / {@code MODULE} / {@code FUNCTION}
 * @param scopeId   作用域 ID
 * @param enabled   是否启用
 * @param reason    原因说明，可空
 */
public record FeatureToggleView(String scopeType, String scopeId, boolean enabled, String reason) {
}
