package com.foundation.calendar.infrastructure;

import com.foundation.calendar.domain.CalendarDay;

import java.util.List;

/** 日历日仓储（F-09 BAS-18 §18.1）：持久化边界，实现走 MyBatis-Plus（48-CS §4）。 */
public interface CalendarDayRepository {

    /** 某日历的全部日（缓存装载用）。 */
    List<CalendarDay> findByCalendar(String calendarId);

    /** 新增或覆盖某日类型。 */
    void upsert(String calendarId, CalendarDay day);
}
