package com.foundation.starter.client.calendar;

import java.time.LocalDate;
import java.util.List;

/**
 * 工作日历读取与推算（F-09 BAS-18 §18.1）：供应用侧（OA / 审批 / 流程）计算期限、逾期与 SLA。
 */
public interface CalendarClient {

    /** 日历头列表（{@code region} / {@code year} 均可空表示全量）。 */
    List<CalendarInfo> calendars(String region, Integer year);

    /** 单日是否工作日。 */
    WorkdayResult workday(String region, LocalDate date);

    /** {@code from} 起第 {@code days} 个工作日后的日期（{@code days=0} 返回 {@code from}）。 */
    WorkdayShift addWorkdays(String region, LocalDate from, int days);

    /** 两个日期之间的工作日数（区间 {@code [from, to)}，不含 {@code to}）。 */
    WorkdayCount diffWorkdays(String region, LocalDate from, LocalDate to);
}
