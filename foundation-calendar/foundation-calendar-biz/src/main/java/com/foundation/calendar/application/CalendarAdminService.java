package com.foundation.calendar.application;

import com.foundation.calendar.api.CalendarAdminApi;
import com.foundation.calendar.api.CalendarDayCmd;
import com.foundation.calendar.api.CalendarDayView;
import com.foundation.calendar.api.CalendarImportResult;
import com.foundation.calendar.api.event.CalendarChangedEvent;
import com.foundation.calendar.domain.Calendar;
import com.foundation.calendar.domain.CalendarDay;
import com.foundation.calendar.domain.CalendarIdPrefix;
import com.foundation.calendar.domain.CalendarImportParser;
import com.foundation.calendar.domain.CalendarRules;
import com.foundation.calendar.domain.CalendarSource;
import com.foundation.calendar.domain.CalendarStatus;
import com.foundation.calendar.domain.DayType;
import com.foundation.calendar.infrastructure.CalendarDayRepository;
import com.foundation.calendar.infrastructure.CalendarRepository;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.api.event.EventProperties;
import com.foundation.common.api.event.OutboxAppender;
import com.foundation.common.id.Ids;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 工作日历维护服务（F-09 BAS-18 §18.1）：逐日类型维护与官方日历导入。
 *
 * <p>需权限码 {@code fdn:calendar:admin}（控制器拦截）。维护日类型时日历头不存在则按需创建；
 * 变更与业务落库同事务写入 {@code fdn_outbox}（{@code fdn.calendar.changed}），并失效缓存。
 */
public class CalendarAdminService implements CalendarAdminApi {

    private final CalendarRepository calendars;
    private final CalendarDayRepository days;
    private final CalendarCache cache;
    private final OutboxAppender outbox;
    private final EventProperties events;

    public CalendarAdminService(CalendarRepository calendars, CalendarDayRepository days, CalendarCache cache,
                                OutboxAppender outbox, EventProperties events) {
        this.calendars = calendars;
        this.days = days;
        this.cache = cache;
        this.outbox = outbox;
        this.events = events;
    }

    @Override
    @Transactional
    public CalendarDayView upsertDay(String region, int year, CalendarDayCmd cmd, String operator) {
        String area = CalendarRules.requireRegion(region);
        int calendarYear = CalendarRules.requireYear(year);
        LocalDate date = CalendarRules.requireDate(cmd.dayDate());
        if (date.getYear() != calendarYear) {
            throw new BizException(ErrorCode.BAD_REQUEST, "日期不在指定年度内：" + date);
        }
        DayType type = DayType.of(cmd.dayType());
        String name = CalendarRules.normalizeName(cmd.name());
        Calendar header = ensureHeader(area, calendarYear, CalendarSource.CUSTOM);
        days.upsert(header.id(), CalendarDay.of(date, type, name));
        cache.invalidate(area, calendarYear);
        publish(header.id(), CalendarChangedEvent.CHANGE_UPSERT_DAY);
        return new CalendarDayView(date, type.name(), type.isWorkday(), name);
    }

    @Override
    @Transactional
    public CalendarImportResult importCalendar(String region, int year, byte[] content, String operator) {
        String area = CalendarRules.requireRegion(region);
        int calendarYear = CalendarRules.requireYear(year);
        List<CalendarImportParser.ParsedDay> parsed = CalendarImportParser.parse(content);
        Calendar header = ensureHeader(area, calendarYear, CalendarSource.OFFICIAL);
        int imported = 0;
        for (CalendarImportParser.ParsedDay day : parsed) {
            if (day.date().getYear() != calendarYear) {
                throw new BizException(ErrorCode.BAD_REQUEST, "导入日期不在指定年度内：" + day.date());
            }
            days.upsert(header.id(), CalendarDay.of(day.date(), day.dayType(), day.name()));
            imported++;
        }
        if (imported > 0) {
            cache.invalidate(area, calendarYear);
            publish(header.id(), CalendarChangedEvent.CHANGE_IMPORTED);
        }
        return new CalendarImportResult(area, calendarYear, imported);
    }

    private Calendar ensureHeader(String region, int year, CalendarSource source) {
        Optional<Calendar> existing = calendars.findByRegionYear(region, year);
        if (existing.isPresent()) {
            Calendar current = existing.get();
            if (source == CalendarSource.OFFICIAL && current.source() != CalendarSource.OFFICIAL) {
                Calendar updated = new Calendar(current.id(), current.regionCode(), current.year(), current.name(),
                        CalendarSource.OFFICIAL, CalendarStatus.ACTIVE, current.createAt(), null);
                calendars.update(updated);
                return updated;
            }
            return current;
        }
        Calendar created = new Calendar(Ids.newId(CalendarIdPrefix.CALENDAR), region, year,
                region + "-" + year, source, CalendarStatus.ACTIVE, null, null);
        calendars.insert(created);
        return created;
    }

    private void publish(String calendarId, String changeType) {
        outbox.append(events.getTopic(), CalendarChangedEvent.EVENT_TYPE, calendarId,
                CalendarChangedEvent.payload(calendarId, 0L, changeType).toMap());
    }
}
