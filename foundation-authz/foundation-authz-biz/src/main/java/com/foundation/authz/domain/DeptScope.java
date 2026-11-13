package com.foundation.authz.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/**
 * 部门范围档位（F-06 BAS-06 §部门五档）。
 *
 * <p>{@link #ALL} 与 {@link #SELF} 为哨兵：前者表示不限部门，后者表示仅本人（部门集合为空）。
 */
public enum DeptScope {

    /** 全部：不限部门。 */
    ALL,
    /** 自定义部门：取 {@code dept_units} 指定的组织单元。 */
    CUSTOM,
    /** 本部门：人员主组织单元。 */
    DEPT,
    /** 本部门及以下：主组织单元子树。 */
    DEPT_AND_SUB,
    /** 本人：仅本人。 */
    SELF;

    /** 解析入参。 */
    public static DeptScope of(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "部门范围不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法部门范围：" + value);
        }
    }
}
