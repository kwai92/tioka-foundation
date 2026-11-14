package com.foundation.calendar.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/**
 * 日历日类型（F-09 BAS-18 §18.1）。
 *
 * <p>{@code WORKDAY} 普通工作日、{@code RESTDAY} 周末休息、{@code HOLIDAY} 法定节假日、
 * {@code ADJUSTED_WORKDAY} 调休上班。
 */
public enum DayType {

    /** 普通工作日。 */
    WORKDAY(true),

    /** 周末休息日。 */
    RESTDAY(false),

    /** 法定节假日。 */
    HOLIDAY(false),

    /** 调休上班日（周末但上班）。 */
    ADJUSTED_WORKDAY(true);

    private final boolean workday;

    DayType(boolean workday) {
        this.workday = workday;
    }

    /** 该类型是否为工作日。 */
    public boolean isWorkday() {
        return workday;
    }

    /** 严格解析（写库口径不允许含糊）：无法识别时抛参数错误。 */
    public static DayType of(String value) {
        if (value != null) {
            String normalized = value.trim();
            for (DayType type : values()) {
                if (type.name().equalsIgnoreCase(normalized)) {
                    return type;
                }
            }
        }
        throw new BizException(ErrorCode.BAD_REQUEST, "未知日历日类型：" + value);
    }
}
