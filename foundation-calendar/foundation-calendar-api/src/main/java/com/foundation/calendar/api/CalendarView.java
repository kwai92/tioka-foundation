package com.foundation.calendar.api;

/**
 * 日历头视图（F-09 BAS-18 §18.1 数据模型 {@code fdn_calendar}）。
 *
 * @param id         日历 ID（{@code cal_…}）
 * @param regionCode 区域编码（默认 {@code CN}）
 * @param year       年度
 * @param name       名称
 * @param source     来源：{@code OFFICIAL} / {@code CUSTOM}
 * @param status     状态：{@code ACTIVE} / {@code INACTIVE}
 */
public record CalendarView(String id, String regionCode, int year, String name, String source, String status) {
}
