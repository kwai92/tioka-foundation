package com.foundation.authz.domain;

/** 权限中心 ID 前缀（F-05 BAS-05 §领域模型；F-06 BAS-06 数据权限前缀见下）。 */
public final class AuthzIdPrefix {

    public static final String MENU = "men";
    public static final String PERMISSION_CODE = "pms";
    public static final String MANIFEST = "mft";
    public static final String GRANT = "grt";

    /** 数据维度（{@code fdn_data_dimension}）。 */
    public static final String DATA_DIMENSION = "fdd";
    /** 维度值（{@code fdn_dimension_value}）。 */
    public static final String DIMENSION_VALUE = "fdv";
    /** 角色数据范围（{@code fdn_role_data_scope}）。 */
    public static final String ROLE_DATA_SCOPE = "frs";
    /** 角色维度取值（{@code fdn_role_dimension_value}）。 */
    public static final String ROLE_DIMENSION_VALUE = "frv";
    /** 人员维度授予（{@code fdn_person_dimension}）。 */
    public static final String PERSON_DIMENSION = "fpd";
    /** 授权快照（{@code fdn_authz_snapshot}）。 */
    public static final String SNAPSHOT = "fsn";

    private AuthzIdPrefix() {
    }
}
