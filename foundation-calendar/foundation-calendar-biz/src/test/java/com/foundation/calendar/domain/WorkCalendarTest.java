package com.foundation.calendar.domain;

import com.foundation.common.api.error.BizException;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-09 BAS-18 §18.1：工作日推算（含跨春节的「10 个工作日后」）。 */
class WorkCalendarTest {

    /**
     * 2026 春节口径：02-14（周六）调休上班、02-16~02-20 放假、02-22（周日）调休上班；
     * 其余按周末休息、工作日上班。
     */
    private static Predicate<LocalDate> springFestival() {
        Map<LocalDate, Boolean> overrides = Map.of(
                LocalDate.of(2026, 2, 14), true,
                LocalDate.of(2026, 2, 16), false,
                LocalDate.of(2026, 2, 17), false,
                LocalDate.of(2026, 2, 18), false,
                LocalDate.of(2026, 2, 19), false,
                LocalDate.of(2026, 2, 20), false,
                LocalDate.of(2026, 2, 22), true);
        return date -> overrides.getOrDefault(date, CalendarRules.defaultWorkday(date));
    }

    @Test
    void addWorkdaysAcrossSpringFestival() {
        LocalDate from = LocalDate.of(2026, 2, 13);

        LocalDate result = WorkCalendar.addWorkdays(from, 10, springFestival());

        assertThat(result).isEqualTo(LocalDate.of(2026, 3, 4));
    }

    @Test
    void addWorkdaysZeroReturnsSameDay() {
        LocalDate from = LocalDate.of(2026, 2, 13);

        assertThat(WorkCalendar.addWorkdays(from, 0, springFestival())).isEqualTo(from);
    }

    @Test
    void addWorkdaysSkipsWeekend() {
        // 2026-03-06 为周五，次个工作日为 03-09（周一）。
        LocalDate result = WorkCalendar.addWorkdays(LocalDate.of(2026, 3, 6), 1, WorkCalendarTest::weekday);
        assertThat(result).isEqualTo(LocalDate.of(2026, 3, 9));
    }

    @Test
    void diffWorkdaysCountsHalfOpenRange() {
        int count = WorkCalendar.diffWorkdays(LocalDate.of(2026, 2, 13), LocalDate.of(2026, 2, 23),
                springFestival());

        // 02-13（五）、02-14（调休上班）、02-22（调休上班）；02-16~02-20 放假。
        assertThat(count).isEqualTo(3);
    }

    @Test
    void diffWorkdaysIsZeroForEmptyRange() {
        LocalDate day = LocalDate.of(2026, 2, 13);
        assertThat(WorkCalendar.diffWorkdays(day, day, springFestival())).isZero();
    }

    @Test
    void rejectsReversedRangeAndNegativeDays() {
        assertThatThrownBy(() -> WorkCalendar.diffWorkdays(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 1),
                springFestival())).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> WorkCalendar.addWorkdays(LocalDate.of(2026, 3, 1), -1, springFestival()))
                .isInstanceOf(BizException.class);
    }

    private static boolean weekday(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }
}
