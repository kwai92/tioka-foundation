package com.foundation.org.api.dto;

/** 变更历史动作（F-03 §2.3 {@code fdn_org_change_log.action}）。 */
public enum ChangeAction {

    /** 新增。 */
    CREATE,

    /** 修改。 */
    UPDATE,

    /** 移动（组织单元换上级）。 */
    MOVE,

    /** 停用。 */
    DISABLE,

    /** 导入（一次导入一条）。 */
    IMPORT
}
