package com.foundation.calendar.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/**
 * 日历来源（F-09 BAS-18 §18.1）：{@code OFFICIAL} 导入的官方日历、{@code CUSTOM} 企业自定义。
 */
public enum CalendarSource {

    /** 官方日历（导入）。 */
    OFFICIAL,

    /** 企业自定义。 */
    CUSTOM;

    /** 严格解析：无法识别时抛参数错误。 */
    public static CalendarSource of(String value) {
        if (value != null) {
            String normalized = value.trim();
            for (CalendarSource source : values()) {
                if (source.name().equalsIgnoreCase(normalized)) {
                    return source;
                }
            }
        }
        throw new BizException(ErrorCode.BAD_REQUEST, "未知日历来源：" + value);
    }
}
