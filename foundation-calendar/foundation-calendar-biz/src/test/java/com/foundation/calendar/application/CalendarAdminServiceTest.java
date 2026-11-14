package com.foundation.calendar.application;

import com.foundation.calendar.api.CalendarDayCmd;
import com.foundation.calendar.api.CalendarDayView;
import com.foundation.calendar.api.CalendarImportResult;
import com.foundation.calendar.api.event.CalendarChangedEvent;
import com.foundation.calendar.domain.Calendar;
import com.foundation.calendar.domain.CalendarSource;
import com.foundation.calendar.domain.DayType;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.event.EventProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-09 BAS-18 §18.1：日历维护与官方日历导入（含变更事件）。 */
class CalendarAdminServiceTest {

    private final CalendarTestDoubles.InMemoryCalendarRepository calendars =
            new CalendarTestDoubles.InMemoryCalendarRepository();
    private final CalendarTestDoubles.InMemoryCalendarDayRepository days =
            new CalendarTestDoubles.InMemoryCalendarDayRepository();
    private final CalendarTestDoubles.RecordingOutbox outbox = new CalendarTestDoubles.RecordingOutbox();
    private final EventProperties events = new EventProperties();

    private CalendarCache cache() {
        return new CalendarCache(calendars, days, "CN", Duration.ofMinutes(10));
    }

    private CalendarAdminService service() {
        return new CalendarAdminService(calendars, days, cache(), outbox, events);
    }

    @Test
    void upsertDayCreatesCustomHeaderAndPublishesChange() {
        CalendarDayView view = service().upsertDay("CN", 2026,
                new CalendarDayCmd(LocalDate.of(2026, 2, 16), "HOLIDAY", "春节"), "alice");

        assertThat(view).isEqualTo(new CalendarDayView(LocalDate.of(2026, 2, 16), "HOLIDAY", false, "春节"));
        assertThat(calendars.byId).hasSize(1);
        assertThat(calendars.byId.values().iterator().next().source()).isEqualTo(CalendarSource.CUSTOM);
        assertThat(days.upserts).hasSize(1);
        assertThat(outbox.appended).hasSize(1);
        CalendarTestDoubles.RecordingOutbox.Append append = outbox.appended.get(0);
        assertThat(append.topic()).isEqualTo(events.getTopic());
        assertThat(append.eventType()).isEqualTo(CalendarChangedEvent.EVENT_TYPE);
        assertThat(append.payload().get("changeType")).isEqualTo(CalendarChangedEvent.CHANGE_UPSERT_DAY);
        assertThat(append.aggregate()).isEqualTo(append.payload().get("id"));
    }

    @Test
    void upsertDayRejectsDateOutsideYear() {
        assertThatThrownBy(() -> service().upsertDay("CN", 2026,
                new CalendarDayCmd(LocalDate.of(2027, 1, 1), "HOLIDAY", null), "alice"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("年度");
        assertThat(calendars.byId).isEmpty();
        assertThat(outbox.appended).isEmpty();
    }

    @Test
    void importCalendarCreatesOfficialHeaderAndCountsRows() {
        String csv = """
                # 2026 春节
                2026-02-16,HOLIDAY,春节
                2026-02-17,HOLIDAY
                """;

        CalendarImportResult result = service().importCalendar("CN", 2026,
                csv.getBytes(StandardCharsets.UTF_8), "alice");

        assertThat(result).isEqualTo(new CalendarImportResult("CN", 2026, 2));
        assertThat(days.upserts).extracting(day -> day.dayType()).containsExactly(DayType.HOLIDAY, DayType.HOLIDAY);
        assertThat(calendars.byId.values().iterator().next().source()).isEqualTo(CalendarSource.OFFICIAL);
        assertThat(outbox.appended).hasSize(1);
        assertThat(outbox.appended.get(0).payload().get("changeType"))
                .isEqualTo(CalendarChangedEvent.CHANGE_IMPORTED);
    }

    @Test
    void importUpgradesCustomHeaderToOfficial() {
        CalendarAdminService service = service();
        service.upsertDay("CN", 2026,
                new CalendarDayCmd(LocalDate.of(2026, 2, 14), "ADJUSTED_WORKDAY", "调休"), "alice");
        Calendar before = calendars.byId.values().iterator().next();
        assertThat(before.source()).isEqualTo(CalendarSource.CUSTOM);

        service.importCalendar("CN", 2026, "2026-02-16,HOLIDAY\n".getBytes(StandardCharsets.UTF_8), "alice");

        assertThat(calendars.byId).hasSize(1);
        assertThat(calendars.byId.get(before.id()).source()).isEqualTo(CalendarSource.OFFICIAL);
        assertThat(calendars.updates).isEqualTo(1);
    }

    @Test
    void importRejectsDateOutsideYear() {
        assertThatThrownBy(() -> service().importCalendar("CN", 2026,
                "2027-01-01,HOLIDAY\n".getBytes(StandardCharsets.UTF_8), "alice"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void importWithNoRowsDoesNotPublish() {
        CalendarImportResult result = service().importCalendar("CN", 2026, new byte[0], "alice");

        assertThat(result.importedDays()).isZero();
        assertThat(outbox.appended).isEmpty();
    }

    @Test
    void upsertDayInvalidatesReadCache() {
        CalendarCache cache = cache();
        CalendarAdminService service = new CalendarAdminService(calendars, days, cache, outbox, events);
        assertThat(cache.forYear("CN", 2026).isWorkday(LocalDate.of(2026, 2, 16))).isTrue();

        service.upsertDay("CN", 2026,
                new CalendarDayCmd(LocalDate.of(2026, 2, 16), "HOLIDAY", "春节"), "alice");

        assertThat(cache.forYear("CN", 2026).isWorkday(LocalDate.of(2026, 2, 16))).isFalse();
    }
}
