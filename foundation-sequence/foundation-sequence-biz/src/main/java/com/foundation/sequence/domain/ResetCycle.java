package com.foundation.sequence.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 编号重置周期（F-09 BAS-18 §18.2）：决定 {@code period_key}，周期切换自然落到新行、无需定时任务。
 */
public enum ResetCycle {

    /** 不重置。 */
    NONE(DateTimeFormatter.ofPattern("'-'")),

    /** 按日重置（{@code yyyyMMdd}）。 */
    DAILY(DateTimeFormatter.ofPattern("yyyyMMdd")),

    /** 按月重置（{@code yyyyMM}）。 */
    MONTHLY(DateTimeFormatter.ofPattern("yyyyMM")),

    /** 按年重置（{@code yyyy}）。 */
    YEARLY(DateTimeFormatter.ofPattern("yyyy"));

    private final DateTimeFormatter formatter;

    ResetCycle(DateTimeFormatter formatter) {
        this.formatter = formatter;
    }

    /** 按日期取周期键：{@code -} / {@code yyyyMMdd} / {@code yyyyMM} / {@code yyyy}。 */
    public String periodKey(LocalDate date) {
        return formatter.format(date);
    }

    /** 严格解析：无法识别时抛参数错误。 */
    public static ResetCycle of(String value) {
        if (value != null) {
            String normalized = value.trim();
            for (ResetCycle cycle : values()) {
                if (cycle.name().equalsIgnoreCase(normalized)) {
                    return cycle;
                }
            }
        }
        throw new BizException(ErrorCode.BAD_REQUEST, "未知重置周期：" + value);
    }
}
