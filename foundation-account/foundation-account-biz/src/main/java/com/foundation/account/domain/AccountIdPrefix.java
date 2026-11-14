package com.foundation.account.domain;

/** 账号域 ID 前缀（F-04 §1、§4 硬约束）。 */
public final class AccountIdPrefix {

    /** 账号（{@code fdn_account}）。 */
    public static final String ACCOUNT = "acc";

    /** 外部身份（{@code fdn_external_identity}）。 */
    public static final String EXTERNAL_IDENTITY = "xid";

    /** 账号待发记录（{@code fdn_account_push}）。 */
    public static final String PUSH = "apu";

    /** 每日对账记录（{@code fdn_account_reconcile}）。 */
    public static final String RECONCILE = "rec";

    /** 应急管理员启用申请（Redis 一次性凭据，F-04 §7 双人确认第一步）。 */
    public static final String EMERGENCY_REQUEST = "emr";

    /** 交接待办（{@code fdn_handover_todo}，F-04 BAS-14 §1 第 3 步）。 */
    public static final String HANDOVER_TODO = "hdo";

    private AccountIdPrefix() {
    }
}
