package com.foundation.org.api.dto;

/**
 * 来源标识（F-03 §2.1）：{@code (source, external_id)} 唯一，作为同步与导入的幂等键。
 */
public final class OrgSource {

    /** 管理台录入 / Excel 导入。 */
    public static final String LOCAL = "local";

    /** 飞书。 */
    public static final String FEISHU = "feishu";

    /** LDAP。 */
    public static final String LDAP = "ldap";

    /** SCIM。 */
    public static final String SCIM = "scim";

    /** Tioka 数字员工（Agent 上岗）。 */
    public static final String TIOKA_AGENT = "tioka:agent";

    private OrgSource() {
    }
}
