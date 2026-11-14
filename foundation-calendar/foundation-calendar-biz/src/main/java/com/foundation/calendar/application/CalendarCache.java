package com.foundation.calendar.application;

import com.foundation.calendar.domain.Calendar;
import com.foundation.calendar.domain.CalendarDay;
import com.foundation.calendar.domain.CalendarRules;
import com.foundation.calendar.domain.CalendarStatus;
import com.foundation.calendar.domain.DayType;
import com.foundation.calendar.infrastructure.CalendarDayRepository;
import com.foundation.calendar.infrastructure.CalendarRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作日历读取缓存（F-09 BAS-18 §18.1：结果按 {@code (region, year)} 缓存）。
 *
 * <p>装载「区域 + 年度」的日历头与逐日覆盖；未配置区域回退默认区域（{@code regionFallback}）。
 * 写操作按 {@code (region, year)} 失效。TTL 为零或负值表示不缓存。
 */
public class CalendarCache {

    /**
     * 某区域某年度的生效日历。
     *
     * @param region     生效区域（未配置时回退默认区域）
     * @param year       年度
     * @param configured 该区域该年度是否已配置日历头
     * @param overrides  逐日覆盖（日期 → 日历日）
     */
    public record YearCalendar(String region, int year, boolean configured, Map<LocalDate, CalendarDay> overrides) {

        /** 单日是否工作日：命中覆盖按覆盖，否则按默认周规则。 */
        public boolean isWorkday(LocalDate date) {
            CalendarDay override = overrides.get(date);
            if (override != null) {
                return override.workday();
            }
            return CalendarRules.defaultWorkday(date);
        }

        /** 单日类型：命中覆盖按覆盖，否则按默认周规则（周末 {@code RESTDAY}、其余 {@code WORKDAY}）。 */
        public CalendarDay dayOf(LocalDate date) {
            CalendarDay override = overrides.get(date);
            if (override != null) {
                return override;
            }
            return CalendarDay.of(date,
                    CalendarRules.defaultWorkday(date) ? DayType.WORKDAY : DayType.RESTDAY, null);
        }
    }

    private final CalendarRepository calendars;
    private final CalendarDayRepository days;
    private final String defaultRegion;
    private final Duration ttl;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public CalendarCache(CalendarRepository calendars, CalendarDayRepository days,
                         String defaultRegion, Duration ttl) {
        this.calendars = calendars;
        this.days = days;
        this.defaultRegion = CalendarRules.requireRegion(defaultRegion);
        this.ttl = ttl;
    }

    /** 取「区域 + 年度」的生效日历（带缓存与默认区域回退）。 */
    public YearCalendar forYear(String region, int year) {
        String key = region + "#" + year;
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return load(region, year);
        }
        long now = System.nanoTime();
        Entry cached = entries.get(key);
        if (cached != null && cached.expiresAtNanos() > now) {
            return cached.value();
        }
        YearCalendar loaded = load(region, year);
        entries.put(key, new Entry(loaded, now + ttl.toNanos()));
        return loaded;
    }

    /** 写操作后按 {@code (region, year)} 失效。 */
    public void invalidate(String region, int year) {
        entries.remove(region + "#" + year);
    }

    private YearCalendar load(String region, int year) {
        Optional<Calendar> header = calendars.findByRegionYear(region, year);
        if (header.isPresent()) {
            Calendar calendar = header.get();
            return calendar.status() == CalendarStatus.ACTIVE
                    ? loadOverrides(calendar)
                    : new YearCalendar(region, year, false, Map.of());
        }
        if (!region.equals(defaultRegion)) {
            // 未配置区域回退默认区域（F-09 BAS-18 regionFallback）
            Optional<Calendar> fallback = calendars.findByRegionYear(defaultRegion, year);
            if (fallback.isPresent() && fallback.get().status() == CalendarStatus.ACTIVE) {
                return loadOverrides(fallback.get());
            }
        }
        return new YearCalendar(defaultRegion, year, false, Map.of());
    }

    private YearCalendar loadOverrides(Calendar calendar) {
        List<CalendarDay> all = days.findByCalendar(calendar.id());
        Map<LocalDate, CalendarDay> overrides = new HashMap<>();
        for (CalendarDay day : all) {
            overrides.put(day.dayDate(), day);
        }
        return new YearCalendar(calendar.regionCode(), calendar.year(), true, Map.copyOf(overrides));
    }

    private record Entry(YearCalendar value, long expiresAtNanos) {
    }
}
