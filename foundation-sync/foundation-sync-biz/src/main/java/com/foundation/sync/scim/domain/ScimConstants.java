package com.foundation.sync.scim.domain;

import java.util.Set;

/** SCIM 服务端常量（F-08 BAS-13）。 */
public final class ScimConstants {

    /** SCIM 推送在同步侧的来源标识（{@code fdn_sync_source.source_type} 口径）。 */
    public static final String SOURCE = "SCIM";

    /** SCIM 外部身份来源（{@code fdn_external_identity.source}，见 V11 检查约束）。 */
    public static final String EXTERNAL_IDENTITY_SOURCE = "SCIM_EXTERNAL_ID";

    /** 外部身份关联方式（已有人员自动关联）。 */
    public static final String LINK_MODE_AUTO = "AUTO";

    /** 用户资源路径。 */
    public static final String USERS_PATH = "/scim/v2/Users";

    /** 组资源路径。 */
    public static final String GROUPS_PATH = "/scim/v2/Groups";

    /** 用户资源过滤白名单属性。 */
    public static final Set<String> USER_FILTER_ATTRIBUTES = Set.of("userName", "externalId", "displayName");

    /** 组资源过滤白名单属性。 */
    public static final Set<String> GROUP_FILTER_ATTRIBUTES = Set.of("displayName", "externalId");

    /** 用户资源排序白名单属性。 */
    public static final Set<String> USER_SORT_ATTRIBUTES = Set.of("userName", "externalId", "displayName");

    /** 组资源排序白名单属性。 */
    public static final Set<String> GROUP_SORT_ATTRIBUTES = Set.of("displayName", "externalId");

    /** 组织单元扩展属性 URN（SCIM Group 的上级部门与类型）。 */
    public static final String ORG_UNIT_EXTENSION =
            "urn:foundation:params:scim:schemas:extension:orgUnit:2.0";

    /** 用户扩展属性 URN（工号）。 */
    public static final String USER_EXTENSION = "urn:foundation:params:scim:schemas:extension:user:2.0";

    /** 人员状态：启用。 */
    public static final String PERSON_ACTIVE = "ACTIVE";

    /** 人员状态：停用。 */
    public static final String PERSON_DISABLED = "DISABLED";

    /** 人员类型缺省值（{@code ck_person_type}）。 */
    public static final String DEFAULT_PERSON_TYPE = "EMPLOYEE";

    /** 组织单元类型缺省值（部门作为组，{@code ck_org_unit_type}）。 */
    public static final String DEFAULT_ORG_UNIT_TYPE = "DEPARTMENT";

    /** 组织单元类型白名单（{@code ck_org_unit_type}）。 */
    public static final Set<String> ORG_UNIT_TYPES = Set.of("GROUP", "COMPANY", "DEPARTMENT");

    /** 令牌识别前缀长度（明文前若干位，用于定位待校验令牌）。 */
    public static final int TOKEN_PREFIX_LENGTH = 12;

    private ScimConstants() {
    }
}
