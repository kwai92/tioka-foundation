package com.foundation.account.api.dto;

/** 外部身份关联方式（F-04 §2；{@code ck_external_identity_link_mode} 约束）。 */
public enum LinkMode {

    /** 按工号或已验证邮箱自动关联。 */
    AUTO,

    /** 本人用该来源登录后确认。 */
    SELF_CONFIRMED,

    /** 管理员确认。 */
    ADMIN
}
