package com.foundation.calendar.api;

/**
 * 工作日历维护契约（F-09 BAS-18 §18.1）：逐日类型维护与官方日历导入。
 *
 * <p>需权限码 {@code fdn:calendar:admin}。
 */
public interface CalendarAdminApi {

    /** 维护某日类型（节假日 / 调休）；日历头不存在时按需创建（{@code source=CUSTOM}）。 */
    CalendarDayView upsertDay(String region, int year, CalendarDayCmd cmd, String operator);

    /** 导入官方日历（CSV：{@code yyyy-MM-dd,DAY_TYPE[,name]} 每行一天），返回导入天数。 */
    CalendarImportResult importCalendar(String region, int year, byte[] content, String operator);
}
