package com.foundation.account.domain;

/**
 * 账号推送到 Keycloak 的动作类型（F-04 §5；{@code fdn_account_push.action}）。
 *
 * <p>所有写操作先落 Foundation 库、再按本动作推送 Keycloak；推送失败进待发记录重试。
 */
public enum PushAction {

    /** 建 Keycloak 用户。 */
    CREATE_USER,

    /** 设置 {@code enabled}（停用 / 启用）。 */
    SET_ENABLED,

    /** 设置必需动作（首次改密等）。 */
    SET_REQUIRED_ACTIONS,

    /** 重置口令（邮件或临时口令）。 */
    RESET_PASSWORD,

    /** 吊销会话。 */
    REVOKE_SESSIONS
}
