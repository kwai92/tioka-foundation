package com.foundation.calendar.api;

import java.time.LocalDate;

/**
 * 维护某日类型的命令（F-09 BAS-18 §18.1）。
 *
 * @param dayDate 日期
 * @param dayType 类型：{@code WORKDAY} / {@code RESTDAY} / {@code HOLIDAY} / {@code ADJUSTED_WORKDAY}
 * @param name    名称，可空
 */
public record CalendarDayCmd(LocalDate dayDate, String dayType, String name) {
}
