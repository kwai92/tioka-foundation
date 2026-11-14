package com.foundation.account.domain;

/**
 * 账号推送状态（F-04 §5；{@code fdn_account_push.status}）。
 *
 * <p>所有写操作先落 Foundation 库、再推送 Keycloak；推送失败进待发记录重试，对账任务兜底修复。
 */
public enum PushStatus {

    /** 待推送（含失败后等待重试）。 */
    PENDING,

    /** 已成功推送。 */
    SENT,

    /** 推送失败且不再自动重试。 */
    FAILED
}
