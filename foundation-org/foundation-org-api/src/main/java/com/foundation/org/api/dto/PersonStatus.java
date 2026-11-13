package com.foundation.org.api.dto;

/** 人员状态（F-03 §2.1；{@code ck_person_status} 约束）。 */
public enum PersonStatus {

    /** 在职。 */
    ACTIVE,

    /** 停用（含数字员工下岗）。 */
    DISABLED,

    /** 离职。 */
    LEFT
}
