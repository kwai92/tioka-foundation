package com.foundation.calendar.api;

/**
 * 官方日历导入结果（F-09 BAS-18 §18.1）。
 *
 * @param region       区域
 * @param year         年度
 * @param importedDays 导入并落地的天数
 */
public record CalendarImportResult(String region, int year, int importedDays) {
}
