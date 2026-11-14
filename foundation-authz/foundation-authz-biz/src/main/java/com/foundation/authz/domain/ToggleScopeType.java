package com.foundation.authz.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/** 功能开关作用域类型（F-05 BAS-27 §功能开关）。 */
public enum ToggleScopeType {

    /** 子系统级。 */
    SUBSYSTEM,
    /** 模块级。 */
    MODULE,
    /** 功能级。 */
    FUNCTION;

    /** 解析入参；空值或非法值抛 {@code BAD_REQUEST}。 */
    public static ToggleScopeType of(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "开关作用域不能为空（SUBSYSTEM/MODULE/FUNCTION）");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法开关作用域：" + value);
        }
    }
}
