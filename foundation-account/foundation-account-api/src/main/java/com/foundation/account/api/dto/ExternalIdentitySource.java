package com.foundation.account.api.dto;

/** 外部身份来源（F-04 §2；{@code ck_external_identity_source} 约束）。 */
public enum ExternalIdentitySource {

    /** 飞书 open_id。 */
    FEISHU_OPEN_ID,

    /** 飞书 union_id。 */
    FEISHU_UNION_ID,

    /** 钉钉 user_id。 */
    DINGTALK_USER_ID,

    /** 企业微信 user_id。 */
    WECOM_USER_ID,

    /** AD objectGUID。 */
    AD_OBJECTGUID,

    /** SCIM externalId。 */
    SCIM_EXTERNAL_ID
}
