package com.foundation.authz.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/** 数据维度的值来源（F-06 BAS-06 §数据模型）。 */
public enum ValueSource {

    /** 字典：取自 BAS-09 字典。 */
    DICT,
    /** 组织树：直接引用 BAS-02 的组织单元。 */
    ORG_TREE,
    /** 应用上报：经 {@code values:sync} 接口增量维护。 */
    APP_REPORTED;

    /** 解析入参。 */
    public static ValueSource of(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "值来源不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法值来源：" + value);
        }
    }
}
