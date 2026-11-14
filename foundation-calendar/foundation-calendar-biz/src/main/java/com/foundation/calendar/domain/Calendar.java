package com.foundation.calendar.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工作日历头（{@code fdn_calendar} 一行的领域视图，F-09 BAS-18 §18.1）。
 *
 * @param id         日历 ID（{@code cal_…}）
 * @param regionCode 区域编码
 * @param year       年度
 * @param name       名称
 * @param source     来源
 * @param status     状态
 * @param createAt   创建时间
 * @param updateAt   更新时间
 */
public record Calendar(String id,
                       String regionCode,
                       int year,
                       String name,
                       CalendarSource source,
                       CalendarStatus status,
                       LocalDateTime createAt,
                       LocalDateTime updateAt) {
}
