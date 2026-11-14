package com.foundation.authz.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/** 高风险授权复核状态（F-09 BAS-19 §数据模型）。 */
public enum ReviewStatus {

    /** 待复核（授权尚未生效）。 */
    PENDING,
    /** 已放行（授权生效）。 */
    APPROVED,
    /** 已拒绝（授权永不生效）。 */
    REJECTED;

    /** 解析入参；非法值抛 {@code BAD_REQUEST}。 */
    public static ReviewStatus of(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "复核状态不能为空（PENDING/APPROVED/REJECTED）");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法复核状态：" + value);
        }
    }
}
