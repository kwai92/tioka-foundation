package com.foundation.org.api.dto;

/** 人员类型（F-03 §2.1、§3；{@code ck_person_type} 约束）。 */
public enum PersonType {

    /** 正式员工。 */
    EMPLOYEE,

    /** 外包。 */
    OUTSOURCED,

    /** 数字员工（Tioka 已发布 Agent，ADR-032）。 */
    DIGITAL_EMPLOYEE
}
