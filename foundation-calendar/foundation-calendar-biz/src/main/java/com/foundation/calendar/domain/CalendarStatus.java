package com.foundation.calendar.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/**
 * 日历状态（F-09 BAS-18 §18.1）：{@code ACTIVE} 启用、{@code INACTIVE} 停用。
 */
public enum CalendarStatus {

    /** 启用。 */
    ACTIVE,

    /** 停用。 */
    INACTIVE;

    /** 宽松解析（读库口径）：无法识别时按 {@code ACTIVE}。 */
    public static CalendarStatus of(String value) {
        if (value != null) {
            String normalized = value.trim();
            for (CalendarStatus status : values()) {
                if (status.name().equalsIgnoreCase(normalized)) {
                    return status;
                }
            }
        }
        return ACTIVE;
    }

    /** 严格解析：无法识别时抛参数错误。 */
    public static CalendarStatus require(String value) {
        if (value != null) {
            String normalized = value.trim();
            for (CalendarStatus status : values()) {
                if (status.name().equalsIgnoreCase(normalized)) {
                    return status;
                }
            }
        }
        throw new BizException(ErrorCode.BAD_REQUEST, "未知日历状态：" + value);
    }
}
