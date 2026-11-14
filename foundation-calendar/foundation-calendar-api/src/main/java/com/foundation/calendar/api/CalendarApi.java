package com.foundation.calendar.api;

import java.time.LocalDate;
import java.util.List;

/**
 * 工作日历读取与推算契约（F-09 BAS-18 §18.1）。
 *
 * <p>供期限、逾期、SLA 计算调用（S15 §13）：单日判定、加/减工作日、区间工作日数。
 * 只提供日历与计算，不含审批 / 排期引擎。
 */
public interface CalendarApi {

    /** 日历头列表（可按区域与年度过滤；两者均可空表示全量）。 */
    List<CalendarView> calendars(String region, Integer year);

    /** 指定「区域 + 年度」日历在 {@code [from, to]} 内的每日类型（含端点）。 */
    List<CalendarDayView> days(String region, int year, LocalDate from, LocalDate to);

    /** 单日是否工作日。 */
    WorkdayView isWorkday(String region, LocalDate date);

    /** {@code from} 起第 {@code days} 个工作日后的日期（{@code days=0} 返回 {@code from}）。 */
    WorkdayShiftView addWorkdays(String region, LocalDate from, int days);

    /** 两个日期之间的工作日数（区间 {@code [from, to)}，不含 {@code to}）。 */
    WorkdayCountView diffWorkdays(String region, LocalDate from, LocalDate to);
}
