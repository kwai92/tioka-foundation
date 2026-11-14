package com.foundation.calendar.api;

import java.time.LocalDate;

/**
 * 单日工作日判定结果（F-09 BAS-18 §18.1）。
 *
 * @param region  生效区域（未配置时回退 {@code foundation.calendar.default-region}）
 * @param date    日期
 * @param workday 是否工作日
 */
public record WorkdayView(String region, LocalDate date, boolean workday) {
}
