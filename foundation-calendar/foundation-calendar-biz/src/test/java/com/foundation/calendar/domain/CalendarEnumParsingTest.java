package com.foundation.calendar.domain;

import com.foundation.common.api.error.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-09 BAS-18 §18.1：日历枚举解析口径（严格写库 / 宽松读库）。 */
class CalendarEnumParsingTest {

    @Test
    void dayTypeIsStrictAndCarriesWorkdayFlag() {
        assertThat(DayType.of("holiday")).isEqualTo(DayType.HOLIDAY);
        assertThat(DayType.WORKDAY.isWorkday()).isTrue();
        assertThat(DayType.ADJUSTED_WORKDAY.isWorkday()).isTrue();
        assertThat(DayType.RESTDAY.isWorkday()).isFalse();
        assertThat(DayType.HOLIDAY.isWorkday()).isFalse();
        assertThatThrownBy(() -> DayType.of("UNKNOWN")).isInstanceOf(BizException.class);
    }

    @Test
    void sourceIsStrict() {
        assertThat(CalendarSource.of(" official ")).isEqualTo(CalendarSource.OFFICIAL);
        assertThatThrownBy(() -> CalendarSource.of("EXTERNAL")).isInstanceOf(BizException.class);
    }

    @Test
    void statusReadsLenientlyButWritesStrictly() {
        assertThat(CalendarStatus.of("inactive")).isEqualTo(CalendarStatus.INACTIVE);
        assertThat(CalendarStatus.of(null)).isEqualTo(CalendarStatus.ACTIVE);
        assertThat(CalendarStatus.of("garbage")).isEqualTo(CalendarStatus.ACTIVE);
        assertThat(CalendarStatus.require("INACTIVE")).isEqualTo(CalendarStatus.INACTIVE);
        assertThatThrownBy(() -> CalendarStatus.require("garbage")).isInstanceOf(BizException.class);
    }
}
