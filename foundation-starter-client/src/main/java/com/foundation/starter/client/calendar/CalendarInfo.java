package com.foundation.starter.client.calendar;

/**
 * 日历头（F-09 BAS-18 §18.1，应用侧视图）。
 *
 * @param id         日历 ID
 * @param regionCode 区域编码
 * @param year       年度
 * @param name       名称
 * @param source     来源：{@code OFFICIAL} / {@code CUSTOM}
 * @param status     状态：{@code ACTIVE} / {@code INACTIVE}
 */
public record CalendarInfo(String id, String regionCode, int year, String name, String source, String status) {
}
