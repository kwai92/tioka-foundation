package com.foundation.calendar.domain;

/**
 * 工作日历主键前缀（00-施工总纲 §2.2：{@code {前缀}_{ulid}}，列宽 {@code VARCHAR(32)}）。
 */
public final class CalendarIdPrefix {

    /** 日历头：{@code cal_{ulid}}。 */
    public static final String CALENDAR = "cal";

    /** 日历日：{@code cdd_{ulid}}。 */
    public static final String CALENDAR_DAY = "cdd";

    private CalendarIdPrefix() {
    }
}
