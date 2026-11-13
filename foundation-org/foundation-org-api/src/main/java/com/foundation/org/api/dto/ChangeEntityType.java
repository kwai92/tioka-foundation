package com.foundation.org.api.dto;

/** 变更历史对象类型（F-03 §2.3 {@code fdn_org_change_log.entity_type}）。 */
public enum ChangeEntityType {

    /** 组织单元。 */
    ORG_UNIT,

    /** 岗位。 */
    POSITION,

    /** 职级。 */
    JOB_GRADE,

    /** 人员。 */
    PERSON,

    /** 任职。 */
    EMPLOYMENT,

    /** 汇报关系。 */
    REPORT_LINE
}
