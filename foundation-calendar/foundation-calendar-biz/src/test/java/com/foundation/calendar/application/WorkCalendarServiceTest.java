package com.foundation.calendar.application;

import com.foundation.calendar.api.CalendarDayView;
import com.foundation.calendar.api.CalendarView;
import com.foundation.calendar.api.WorkdayCountView;
import com.foundation.calendar.api.WorkdayShiftView;
import com.foundation.calendar.api.WorkdayView;
import com.foundation.calendar.config.CalendarProperties;
import com.foundation.calendar.domain.Calendar;
import com.foundation.calendar.domain.CalendarDay;
import com.foundation.calendar.domain.CalendarSource;
import com.foundation.calendar.domain.CalendarStatus;
import com.foundation.calendar.domain.DayType;
import com.foundation.common.api.error.BizException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-09 BAS-18 §18.1：工作日判定与推算（含区域回退）。 */
class WorkCalendarServiceTest {

    private final CalendarTestDoubles.InMemoryCalendarRepository calendars =
            new CalendarTestDoubles.InMemoryCalendarRepository();
    private final CalendarTestDoubles.InMemoryCalendarDayRepository days =
            new CalendarTestDoubles.InMemoryCalendarDayRepository();
    private final CalendarProperties properties = new CalendarProperties();

    private WorkCalendarService service() {
        return new WorkCalendarService(calendars, new CalendarCache(calendars, days, "CN",
                Duration.ofMinutes(10)), properties);
    }

    private void springFestivalCn2026() {
        Calendar calendar = new Calendar("cal_cn_2026", "CN", 2026, "CN-2026",
                CalendarSource.OFFICIAL, CalendarStatus.ACTIVE, null, null);
        calendars.insert(calendar);
        days.upsert(calendar.id(), CalendarDay.of(LocalDate.of(2026, 2, 14), DayType.ADJUSTED_WORKDAY, "调休"));
        days.upsert(calendar.id(), CalendarDay.of(LocalDate.of(2026, 2, 16), DayType.HOLIDAY, "春节"));
        days.upsert(calendar.id(), CalendarDay.of(LocalDate.of(2026, 2, 17), DayType.HOLIDAY, "春节"));
        days.upsert(calendar.id(), CalendarDay.of(LocalDate.of(2026, 2, 18), DayType.HOLIDAY, "春节"));
        days.upsert(calendar.id(), CalendarDay.of(LocalDate.of(2026, 2, 19), DayType.HOLIDAY, "春节"));
        days.upsert(calendar.id(), CalendarDay.of(LocalDate.of(2026, 2, 20), DayType.HOLIDAY, "春节"));
        days.upsert(calendar.id(), CalendarDay.of(LocalDate.of(2026, 2, 22), DayType.ADJUSTED_WORKDAY, "调休"));
    }

    @Test
    void isWorkdayUsesConfiguredOverrides() {
        springFestivalCn2026();

        WorkdayView holiday = service().isWorkday("CN", LocalDate.of(2026, 2, 16));
        WorkdayView adjusted = service().isWorkday("CN", LocalDate.of(2026, 2, 14));

        assertThat(holiday.workday()).isFalse();
        assertThat(holiday.region()).isEqualTo("CN");
        assertThat(adjusted.workday()).isTrue();
    }

    @Test
    void isWorkdayFallsBackToDefaultRegion() {
        springFestivalCn2026();

        WorkdayView view = service().isWorkday("US", LocalDate.of(2026, 2, 16));

        assertThat(view.region()).isEqualTo("CN");
        assertThat(view.workday()).isFalse();
    }

    @Test
    void daysReturnsInclusiveRange() {
        springFestivalCn2026();

        List<CalendarDayView> views = service().days("CN", 2026,
                LocalDate.of(2026, 2, 14), LocalDate.of(2026, 2, 16));

        assertThat(views).hasSize(3);
        assertThat(views).extracting(CalendarDayView::dayType)
                .containsExactly("ADJUSTED_WORKDAY", "RESTDAY", "HOLIDAY");
    }

    @Test
    void addWorkdaysAcrossSpringFestival() {
        springFestivalCn2026();

        WorkdayShiftView view = service().addWorkdays("CN", LocalDate.of(2026, 2, 13), 10);

        assertThat(view.result()).isEqualTo(LocalDate.of(2026, 3, 4));
        assertThat(view.workdays()).isEqualTo(10);
    }

    @Test
    void diffWorkdaysCountsInterval() {
        springFestivalCn2026();

        WorkdayCountView view = service().diffWorkdays("CN", LocalDate.of(2026, 2, 13), LocalDate.of(2026, 2, 23));

        assertThat(view.workdays()).isEqualTo(3);
    }

    @Test
    void calendarsListsWithOptionalFilters() {
        springFestivalCn2026();
        calendars.insert(new Calendar("cal_us_2026", "US", 2026, "US-2026",
                CalendarSource.CUSTOM, CalendarStatus.ACTIVE, null, null));
        WorkCalendarService service = service();

        assertThat(service.calendars(null, null)).hasSize(2);
        assertThat(service.calendars("CN", null)).extracting(CalendarView::regionCode).containsExactly("CN");
        assertThat(service.calendars(null, 2026)).hasSize(2);
        assertThat(service.calendars(null, 1999)).isEmpty();
    }

    @Test
    void rejectsOutOfRangeYear() {
        assertThatThrownBy(() -> service().calendars(null, 1500))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("年度");
    }
}
