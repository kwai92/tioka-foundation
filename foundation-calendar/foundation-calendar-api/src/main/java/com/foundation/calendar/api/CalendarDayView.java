package com.foundation.calendar.api;

import java.time.LocalDate;

/**
 * 日历日视图（F-09 BAS-18 §18.1 数据模型 {@code fdn_calendar_day}）。
 *
 * @param dayDate  日期
 * @param dayType  类型：{@code WORKDAY} / {@code RESTDAY} / {@code HOLIDAY} / {@code ADJUSTED_WORKDAY}
 * @param workday  是否工作日（冗余字段，便于快速判定）
 * @param name     名称（如「春节」「国庆调休」），可空
 */
public record CalendarDayView(LocalDate dayDate, String dayType, boolean workday, String name) {
}
