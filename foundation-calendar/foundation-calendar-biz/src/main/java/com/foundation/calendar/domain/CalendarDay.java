package com.foundation.calendar.domain;

import java.time.LocalDate;

/**
 * 日历日（{@code fdn_calendar_day} 一行的领域视图，F-09 BAS-18 §18.1）。
 *
 * @param dayDate  日期
 * @param dayType  类型
 * @param workday  是否工作日（冗余存储）
 * @param name     名称，可空
 */
public record CalendarDay(LocalDate dayDate, DayType dayType, boolean workday, String name) {

    /** 以类型推导工作日标记的构造。 */
    public static CalendarDay of(LocalDate dayDate, DayType dayType, String name) {
        return new CalendarDay(dayDate, dayType, dayType.isWorkday(), name);
    }
}
