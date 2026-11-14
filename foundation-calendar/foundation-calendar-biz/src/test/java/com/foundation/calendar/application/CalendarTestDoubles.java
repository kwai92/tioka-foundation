package com.foundation.calendar.application;

import com.foundation.calendar.domain.Calendar;
import com.foundation.calendar.domain.CalendarDay;
import com.foundation.calendar.infrastructure.CalendarDayRepository;
import com.foundation.calendar.infrastructure.CalendarRepository;
import com.foundation.common.api.event.OutboxAppender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 工作日历应用层单测的内存替身。 */
final class CalendarTestDoubles {

    private CalendarTestDoubles() {
    }

    /** 内存日历头仓储。 */
    static final class InMemoryCalendarRepository implements CalendarRepository {

        final Map<String, Calendar> byId = new LinkedHashMap<>();
        int updates;

        @Override
        public Optional<Calendar> findByRegionYear(String regionCode, int year) {
            return byId.values().stream()
                    .filter(calendar -> calendar.regionCode().equals(regionCode) && calendar.year() == year)
                    .findFirst();
        }

        @Override
        public List<Calendar> list(String regionCode, Integer year) {
            return byId.values().stream()
                    .filter(calendar -> regionCode == null || calendar.regionCode().equals(regionCode))
                    .filter(calendar -> year == null || calendar.year() == year)
                    .toList();
        }

        @Override
        public void insert(Calendar calendar) {
            byId.put(calendar.id(), calendar);
        }

        @Override
        public void update(Calendar calendar) {
            byId.put(calendar.id(), calendar);
            updates++;
        }
    }

    /** 内存日历日仓储。 */
    static final class InMemoryCalendarDayRepository implements CalendarDayRepository {

        final Map<String, CalendarDay> byKey = new LinkedHashMap<>();
        final List<CalendarDay> upserts = new ArrayList<>();

        private static String key(String calendarId, CalendarDay day) {
            return calendarId + "#" + day.dayDate();
        }

        @Override
        public List<CalendarDay> findByCalendar(String calendarId) {
            return byKey.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(calendarId + "#"))
                    .map(Map.Entry::getValue)
                    .sorted(Comparator.comparing(CalendarDay::dayDate))
                    .toList();
        }

        @Override
        public void upsert(String calendarId, CalendarDay day) {
            byKey.put(key(calendarId, day), day);
            upserts.add(day);
        }
    }

    /** 记录型 Outbox。 */
    static final class RecordingOutbox implements OutboxAppender {

        final List<Append> appended = new ArrayList<>();

        @Override
        public void append(String topic, String eventType, String aggregate, Map<String, Object> payload) {
            appended.add(new Append(topic, eventType, aggregate, payload));
        }

        record Append(String topic, String eventType, String aggregate, Map<String, Object> payload) {
        }
    }
}
