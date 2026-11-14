package com.foundation.authz.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/** 自管一次性导入明细项类型（F-05 BAS-27 §自管导入）。 */
public enum SelfImportKind {

    /** 角色。 */
    ROLE,
    /** 授权。 */
    ASSIGNMENT;

    /** 解析入参；空值或非法值抛 {@code BAD_REQUEST}。 */
    public static SelfImportKind of(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "导入明细类型不能为空（ROLE/ASSIGNMENT）");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法导入明细类型：" + value);
        }
    }
}
