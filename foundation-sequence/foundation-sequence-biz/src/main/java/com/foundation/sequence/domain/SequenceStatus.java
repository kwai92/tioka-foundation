package com.foundation.sequence.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/**
 * 编号规则状态（F-09 BAS-18 §18.2）：{@code ACTIVE} 启用、{@code INACTIVE} 停用。
 */
public enum SequenceStatus {

    /** 启用。 */
    ACTIVE,

    /** 停用。 */
    INACTIVE;

    /** 宽松解析（读库口径）：无法识别时按 {@code ACTIVE}。 */
    public static SequenceStatus of(String value) {
        if (value != null) {
            String normalized = value.trim();
            for (SequenceStatus status : values()) {
                if (status.name().equalsIgnoreCase(normalized)) {
                    return status;
                }
            }
        }
        return ACTIVE;
    }

    /** 严格解析：无法识别时抛参数错误。 */
    public static SequenceStatus require(String value) {
        if (value != null) {
            String normalized = value.trim();
            for (SequenceStatus status : values()) {
                if (status.name().equalsIgnoreCase(normalized)) {
                    return status;
                }
            }
        }
        throw new BizException(ErrorCode.BAD_REQUEST, "未知编号规则状态：" + value);
    }
}
