package com.foundation.calendar.application;

import com.foundation.calendar.domain.Calendar;
import com.foundation.calendar.domain.CalendarDay;
import com.foundation.calendar.domain.CalendarSource;
import com.foundation.calendar.domain.CalendarStatus;
import com.foundation.calendar.domain.DayType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** F-09 BAS-18 §18.1：日历读取缓存与区域回退。 */
class CalendarCacheTest {

    private final CalendarTestDoubles.InMemoryCalendarRepository calendars =
            new CalendarTestDoubles.InMemoryCalendarRepository();
    private final CalendarTestDoubles.InMemoryCalendarDayRepository days =
            new CalendarTestDoubles.InMemoryCalendarDayRepository();

    private CalendarCache cache() {
        return new CalendarCache(calendars, days, "CN", Duration.ofMinutes(10));
    }

    private Calendar header(String region, int year, CalendarStatus status) {
        Calendar calendar = new Calendar("cal_" + region + year, region, year, region + "-" + year,
                CalendarSource.OFFICIAL, status, null, null);
        calendars.insert(calendar);
        return calendar;
    }

    @Test
    void configuredRegionHonoursOverrides() {
        Calendar calendar = header("CN", 2026, CalendarStatus.ACTIVE);
        days.upsert(calendar.id(), CalendarDay.of(LocalDate.of(2026, 2, 16), DayType.HOLIDAY, "春节"));

        CalendarCache.YearCalendar year = cache().forYear("CN", 2026);

        assertThat(year.configured()).isTrue();
        assertThat(year.isWorkday(LocalDate.of(2026, 2, 16))).isFalse();
        assertThat(year.dayOf(LocalDate.of(2026, 2, 16)).dayType()).isEqualTo(DayType.HOLIDAY);
        // 无覆盖的平日按默认周规则。
        assertThat(year.isWorkday(LocalDate.of(2026, 2, 13))).isTrue();
        assertThat(year.dayOf(LocalDate.of(2026, 2, 14)).dayType()).isEqualTo(DayType.RESTDAY);
    }

    @Test
    void unconfiguredRegionFallsBackToDefaultRegion() {
        Calendar calendar = header("CN", 2026, CalendarStatus.ACTIVE);
        days.upsert(calendar.id(), CalendarDay.of(LocalDate.of(2026, 2, 16), DayType.HOLIDAY, "春节"));

        CalendarCache.YearCalendar year = cache().forYear("US", 2026);

        assertThat(year.region()).isEqualTo("CN");
        assertThat(year.isWorkday(LocalDate.of(2026, 2, 16))).isFalse();
    }

    @Test
    void inactiveCalendarIsNotConfigured() {
        header("CN", 2026, CalendarStatus.INACTIVE);

        CalendarCache.YearCalendar year = cache().forYear("CN", 2026);

        assertThat(year.configured()).isFalse();
        assertThat(year.isWorkday(LocalDate.of(2026, 2, 16))).isTrue();
    }

    @Test
    void invalidateForcesReload() {
        Calendar calendar = header("CN", 2026, CalendarStatus.ACTIVE);
        CalendarCache cache = cache();
        assertThat(cache.forYear("CN", 2026).isWorkday(LocalDate.of(2026, 2, 16))).isTrue();

        days.upsert(calendar.id(), CalendarDay.of(LocalDate.of(2026, 2, 16), DayType.HOLIDAY, "春节"));
        assertThat(cache.forYear("CN", 2026).isWorkday(LocalDate.of(2026, 2, 16))).isTrue();

        cache.invalidate("CN", 2026);

        assertThat(cache.forYear("CN", 2026).isWorkday(LocalDate.of(2026, 2, 16))).isFalse();
    }

    @Test
    void zeroTtlDisablesCaching() {
        Calendar calendar = header("CN", 2026, CalendarStatus.ACTIVE);
        CalendarCache cache = new CalendarCache(calendars, days, "CN", Duration.ZERO);

        assertThat(cache.forYear("CN", 2026).isWorkday(LocalDate.of(2026, 2, 16))).isTrue();
        days.upsert(calendar.id(), CalendarDay.of(LocalDate.of(2026, 2, 16), DayType.HOLIDAY, "春节"));

        assertThat(cache.forYear("CN", 2026).isWorkday(LocalDate.of(2026, 2, 16))).isFalse();
    }
}
