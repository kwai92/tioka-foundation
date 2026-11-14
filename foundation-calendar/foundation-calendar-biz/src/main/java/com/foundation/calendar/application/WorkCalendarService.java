package com.foundation.calendar.application;

import com.foundation.calendar.api.CalendarApi;
import com.foundation.calendar.api.CalendarDayView;
import com.foundation.calendar.api.CalendarView;
import com.foundation.calendar.api.WorkdayCountView;
import com.foundation.calendar.api.WorkdayShiftView;
import com.foundation.calendar.api.WorkdayView;
import com.foundation.calendar.config.CalendarProperties;
import com.foundation.calendar.domain.Calendar;
import com.foundation.calendar.domain.CalendarDay;
import com.foundation.calendar.domain.CalendarRules;
import com.foundation.calendar.domain.WorkCalendar;
import com.foundation.calendar.infrastructure.CalendarRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作日历读取与推算服务（F-09 BAS-18 §18.1）。
 *
 * <p>工作日的判定与推算经 {@link CalendarCache} 读取（按 {@code (region, year)} 缓存），
 * 推算为纯函数 {@link WorkCalendar}；未配置区域回退 {@code foundation.calendar.default-region}。
 */
public class WorkCalendarService implements CalendarApi {

    private final CalendarRepository calendars;
    private final CalendarCache cache;
    private final String defaultRegion;

    public WorkCalendarService(CalendarRepository calendars, CalendarCache cache, CalendarProperties properties) {
        this.calendars = calendars;
        this.cache = cache;
        this.defaultRegion = properties.getDefaultRegion();
    }

    @Override
    public List<CalendarView> calendars(String region, Integer year) {
        String filter = region == null || region.isBlank() ? null : region.trim();
        Integer filterYear = year == null ? null : CalendarRules.requireYear(year);
        return calendars.list(filter, filterYear).stream().map(WorkCalendarService::toView).toList();
    }

    @Override
    public List<CalendarDayView> days(String region, int year, LocalDate from, LocalDate to) {
        CalendarRules.requireYear(year);
        CalendarRules.requireRange(from, to);
        String effective = CalendarRules.regionOrDefault(region, defaultRegion);
        List<CalendarDayView> views = new ArrayList<>();
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            views.add(toDayView(cache.forYear(effective, cursor.getYear()).dayOf(cursor)));
        }
        return views;
    }

    @Override
    public WorkdayView isWorkday(String region, LocalDate date) {
        CalendarRules.requireDate(date);
        String effective = CalendarRules.regionOrDefault(region, defaultRegion);
        CalendarCache.YearCalendar calendar = cache.forYear(effective, date.getYear());
        return new WorkdayView(calendar.region(), date, calendar.isWorkday(date));
    }

    @Override
    public WorkdayShiftView addWorkdays(String region, LocalDate from, int days) {
        CalendarRules.requireDate(from);
        CalendarRules.requireAddDays(days);
        String effective = CalendarRules.regionOrDefault(region, defaultRegion);
        LocalDate result = WorkCalendar.addWorkdays(from, days,
                date -> cache.forYear(effective, date.getYear()).isWorkday(date));
        return new WorkdayShiftView(cache.forYear(effective, from.getYear()).region(), from, days, result);
    }

    @Override
    public WorkdayCountView diffWorkdays(String region, LocalDate from, LocalDate to) {
        CalendarRules.requireRange(from, to);
        String effective = CalendarRules.regionOrDefault(region, defaultRegion);
        int workdays = WorkCalendar.diffWorkdays(from, to,
                date -> cache.forYear(effective, date.getYear()).isWorkday(date));
        return new WorkdayCountView(cache.forYear(effective, from.getYear()).region(), from, to, workdays);
    }

    private static CalendarView toView(Calendar calendar) {
        return new CalendarView(calendar.id(), calendar.regionCode(), calendar.year(), calendar.name(),
                calendar.source().name(), calendar.status().name());
    }

    private static CalendarDayView toDayView(CalendarDay day) {
        return new CalendarDayView(day.dayDate(), day.dayType().name(), day.workday(), day.name());
    }
}
