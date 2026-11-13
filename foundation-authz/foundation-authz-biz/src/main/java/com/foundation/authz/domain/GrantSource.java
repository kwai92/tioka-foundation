package com.foundation.authz.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/**
 * 授权来源（F-05 BAS-05 §领域模型）。
 *
 * <p>{@link #MANUAL} 为手工授权，规则（{@link #RULE}）与同步（{@link #SYNC}）不得修改或删除它。
 */
public enum GrantSource {

    MANUAL,
    RULE,
    SYNC;

    /** 解析入参；缺省视为 {@link #MANUAL}。 */
    public static GrantSource of(String value) {
        if (value == null || value.isBlank()) {
            return MANUAL;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法授权来源：" + value);
        }
    }
}
