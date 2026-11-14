package com.foundation.starter.client.calendar;

import java.time.LocalDate;

/**
 * 单日工作日判定结果（F-09 BAS-18 §18.1）。
 *
 * @param region  生效区域
 * @param date    日期
 * @param workday 是否工作日
 */
public record WorkdayResult(String region, LocalDate date, boolean workday) {
}
