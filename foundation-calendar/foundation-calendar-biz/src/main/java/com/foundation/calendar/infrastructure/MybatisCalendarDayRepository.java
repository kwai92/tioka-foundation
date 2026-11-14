package com.foundation.calendar.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.calendar.domain.CalendarDay;
import com.foundation.calendar.domain.CalendarIdPrefix;
import com.foundation.calendar.domain.DayType;
import com.foundation.calendar.infrastructure.mapper.CalendarDayMapper;
import com.foundation.calendar.infrastructure.persistence.CalendarDayDO;
import com.foundation.common.id.Ids;

import java.util.List;

/** 日历日仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisCalendarDayRepository implements CalendarDayRepository {

    private final CalendarDayMapper mapper;

    public MybatisCalendarDayRepository(CalendarDayMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<CalendarDay> findByCalendar(String calendarId) {
        return mapper.selectList(Wrappers.<CalendarDayDO>lambdaQuery()
                        .eq(CalendarDayDO::getCalendarId, calendarId)
                        .orderByAsc(CalendarDayDO::getDayDate))
                .stream().map(MybatisCalendarDayRepository::toDomain).toList();
    }

    @Override
    public void upsert(String calendarId, CalendarDay day) {
        CalendarDayDO existing = mapper.selectOne(Wrappers.<CalendarDayDO>lambdaQuery()
                .eq(CalendarDayDO::getCalendarId, calendarId)
                .eq(CalendarDayDO::getDayDate, day.dayDate())
                .last("limit 1"));
        CalendarDayDO row = new CalendarDayDO();
        row.setCalendarId(calendarId);
        row.setDayDate(day.dayDate());
        row.setDayType(day.dayType().name());
        row.setWorkday(day.workday());
        row.setName(day.name());
        if (existing == null) {
            row.setId(Ids.newId(CalendarIdPrefix.CALENDAR_DAY));
            mapper.insert(row);
        } else {
            row.setId(existing.getId());
            mapper.updateById(row);
        }
    }

    private static CalendarDay toDomain(CalendarDayDO row) {
        DayType type = DayType.of(row.getDayType());
        boolean workday = row.getWorkday() == null ? type.isWorkday() : row.getWorkday();
        return new CalendarDay(row.getDayDate(), type, workday, row.getName());
    }
}
