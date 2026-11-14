package com.foundation.calendar.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-09 BAS-18 §18.1：官方日历 CSV 导入解析。 */
class CalendarImportParserTest {

    @Test
    void parsesRowsSkippingCommentsAndBlankLines() {
        String csv = """
                # 2026 春节
                2026-02-14,ADJUSTED_WORKDAY,春节调休上班

                2026-02-16,HOLIDAY,春节
                2026-02-17,HOLIDAY
                """;

        List<CalendarImportParser.ParsedDay> days = CalendarImportParser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(days).hasSize(3);
        assertThat(days.get(0).date()).isEqualTo(LocalDate.of(2026, 2, 14));
        assertThat(days.get(0).dayType()).isEqualTo(DayType.ADJUSTED_WORKDAY);
        assertThat(days.get(0).name()).isEqualTo("春节调休上班");
        assertThat(days.get(2).name()).isNull();
    }

    @Test
    void rejectsMalformedRowWithLineNumber() {
        String csv = """
                2026-02-16,HOLIDAY
                not-a-date,HOLIDAY
                """;

        assertThatThrownBy(() -> CalendarImportParser.parse(csv.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("第 2 行")
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void rejectsUnknownDayType() {
        assertThatThrownBy(() -> CalendarImportParser.parse("2026-02-16,FESTIVAL\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BizException.class);
    }

    @Test
    void emptyContentYieldsNoRows() {
        assertThat(CalendarImportParser.parse(new byte[0])).isEmpty();
        assertThat(CalendarImportParser.parse(null)).isEmpty();
    }
}
