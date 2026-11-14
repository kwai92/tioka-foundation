package com.foundation.account.api.dto;

/** 账号类型（F-04 §1；{@code ck_account_type} 约束）。 */
public enum AccountType {

    /** 个人账号（关联人员，一人最多一个）。 */
    PERSONAL,

    /** 应急管理员（不关联人员，默认停用，启用需双人确认）。 */
    EMERGENCY_ADMIN,

    /** 服务账号（绑定应用，仅 {@code client_credentials}）。 */
    SERVICE
}
