package com.foundation.calendar.domain;

import java.time.LocalDate;
import java.util.function.Predicate;

/**
 * 工作日推算（F-09 BAS-18 §18.1）：纯函数，工作日判定由 {@code isWorkday} 谓词注入。
 *
 * <p>与持久化 / 缓存解耦，便于单测覆盖跨节假日的推算。
 */
public final class WorkCalendar {

    private WorkCalendar() {
    }

    /**
     * {@code from} 起第 {@code days} 个工作日后的日期；{@code days=0} 返回 {@code from}。
     *
     * <p>从 {@code from} 的次日开始逐日前进，遇工作日计数，累计到 {@code days} 即返回。
     */
    public static LocalDate addWorkdays(LocalDate from, int days, Predicate<LocalDate> isWorkday) {
        CalendarRules.requireDate(from);
        CalendarRules.requireAddDays(days);
        LocalDate cursor = from;
        int counted = 0;
        while (counted < days) {
            cursor = cursor.plusDays(1);
            if (isWorkday.test(cursor)) {
                counted++;
            }
        }
        return cursor;
    }

    /** 区间 {@code [from, to)} 内的工作日数（不含 {@code to}）。 */
    public static int diffWorkdays(LocalDate from, LocalDate to, Predicate<LocalDate> isWorkday) {
        CalendarRules.requireRange(from, to);
        int count = 0;
        for (LocalDate cursor = from; cursor.isBefore(to); cursor = cursor.plusDays(1)) {
            if (isWorkday.test(cursor)) {
                count++;
            }
        }
        return count;
    }
}
