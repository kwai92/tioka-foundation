package com.foundation.authz.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/** 权限码风险级别（F-05 BAS-05 §领域模型；高风险授予需二次确认并审计）。 */
public enum RiskLevel {

    LOW,
    MEDIUM,
    HIGH;

    /** 解析清单声明值；缺省视为 {@link #LOW}。 */
    public static RiskLevel of(String value) {
        if (value == null || value.isBlank()) {
            return LOW;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法风险级别：" + value);
        }
    }
}
