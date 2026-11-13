package com.foundation.org.api.dto;

/** 组织单元类型（F-03 §2.1；DB 存 {@code name()}，{@code ck_org_unit_type} 约束）。 */
public enum OrgUnitType {

    /** 集团。 */
    GROUP,

    /** 公司。 */
    COMPANY,

    /** 部门。 */
    DEPARTMENT
}
