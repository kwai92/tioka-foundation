package com.foundation.calendar.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;

/**
 * 日历日实体（{@code fdn_calendar_day}，F-09 BAS-18 §18.1）。
 */
@TableName("fdn_calendar_day")
public class CalendarDayDO {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String calendarId;

    private LocalDate dayDate;

    private String dayType;

    @TableField("is_workday")
    private Boolean workday;

    private String name;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCalendarId() {
        return calendarId;
    }

    public void setCalendarId(String calendarId) {
        this.calendarId = calendarId;
    }

    public LocalDate getDayDate() {
        return dayDate;
    }

    public void setDayDate(LocalDate dayDate) {
        this.dayDate = dayDate;
    }

    public String getDayType() {
        return dayType;
    }

    public void setDayType(String dayType) {
        this.dayType = dayType;
    }

    public Boolean getWorkday() {
        return workday;
    }

    public void setWorkday(Boolean workday) {
        this.workday = workday;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
