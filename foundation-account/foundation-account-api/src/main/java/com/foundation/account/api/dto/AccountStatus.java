package com.foundation.account.api.dto;

/** 账号状态（F-04 §1；{@code ck_account_status} 约束）。锁定与停用分开：锁定可解除，停用需重新开通。 */
public enum AccountStatus {

    /** 正常。 */
    NORMAL,

    /** 锁定（可自动或手动解除）。 */
    LOCKED,

    /** 停用（需重新开通）。 */
    DISABLED
}
