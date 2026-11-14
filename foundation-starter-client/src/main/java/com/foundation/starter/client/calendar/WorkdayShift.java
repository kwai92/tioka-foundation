package com.foundation.starter.client.calendar;

import java.time.LocalDate;

/**
 * 「N 个工作日后」的推算结果（F-09 BAS-18 §18.1）。
 *
 * @param region   生效区域
 * @param from     起始日期
 * @param workdays 工作日数
 * @param result   结果日期
 */
public record WorkdayShift(String region, LocalDate from, int workdays, LocalDate result) {
}
