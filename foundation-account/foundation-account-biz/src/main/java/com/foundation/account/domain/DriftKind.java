package com.foundation.account.domain;

/** 对账差异类别（F-04 §6 对账维度）。 */
public enum DriftKind {

    /** Foundation 有账号但 Keycloak 无对应用户（含尚未推送）。 */
    MISSING_IN_KEYCLOAK,

    /** {@code enabled} 不一致。 */
    ENABLED_MISMATCH,

    /** 登录名（{@code username}）不一致。 */
    USERNAME_MISMATCH,

    /** 必需动作不一致。 */
    REQUIRED_ACTIONS_MISMATCH,

    /** Keycloak 中多出的、非 Foundation 管理的账号。 */
    ORPHAN_IN_KEYCLOAK
}
