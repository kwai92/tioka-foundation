package com.foundation.authz.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/** 授权对象类型（F-05 BAS-05 §领域模型）。 */
public enum GrantSubjectType {

    /** 人员。 */
    PERSON,
    /** 组织单元（可含下级）。 */
    ORG_UNIT,
    /** 岗位。 */
    POST;

    /** 解析入参，非法值抛 {@code BAD_REQUEST}。 */
    public static GrantSubjectType of(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "授权对象类型不能为空（PERSON/ORG_UNIT/POST）");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法授权对象类型：" + value);
        }
    }
}
