package com.foundation.starter.client.calendar;

import java.time.LocalDate;

/**
 * 区间工作日数结果（F-09 BAS-18 §18.1，区间 {@code [from, to)}）。
 *
 * @param region   生效区域
 * @param from     起始日期（含）
 * @param to       结束日期（不含）
 * @param workdays 工作日数
 */
public record WorkdayCount(String region, LocalDate from, LocalDate to, int workdays) {
}
