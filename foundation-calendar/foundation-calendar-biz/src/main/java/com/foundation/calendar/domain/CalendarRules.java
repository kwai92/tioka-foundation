package com.foundation.calendar.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 工作日历领域规则（F-09 BAS-18 §18.1）：入参规范化与不变量校验。
 *
 * <p>不依赖 Web / 安全 / 持久化上下文，供应用服务与控制器共用。
 */
public final class CalendarRules {

    /** 支持的最小年度。 */
    public static final int MIN_YEAR = 1970;

    /** 支持的最大年度。 */
    public static final int MAX_YEAR = 2100;

    private static final int MAX_NAME = 128;

    private CalendarRules() {
    }

    /** 区域编码必填，返回去空白后的值。 */
    public static String requireRegion(String region) {
        if (region == null || region.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "区域编码不能为空");
        }
        return region.trim();
    }

    /** 区域编码：为空时回退默认区域。 */
    public static String regionOrDefault(String region, String defaultRegion) {
        if (region == null || region.isBlank()) {
            return requireRegion(defaultRegion);
        }
        return region.trim();
    }

    /** 年度在支持范围内。 */
    public static int requireYear(int year) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new BizException(ErrorCode.BAD_REQUEST, "年度超出范围：" + year);
        }
        return year;
    }

    /** 日期必填。 */
    public static LocalDate requireDate(LocalDate date) {
        if (date == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "日期不能为空");
        }
        return date;
    }

    /** 区间合法：{@code from <= to}。 */
    public static void requireRange(LocalDate from, LocalDate to) {
        requireDate(from);
        requireDate(to);
        if (to.isBefore(from)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "结束日期早于起始日期");
        }
    }

    /** 工作日数非负。 */
    public static int requireAddDays(int days) {
        if (days < 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "工作日数不能为负");
        }
        return days;
    }

    /** 名称规范化：空 → {@code null}；超长截断到 128。 */
    public static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.length() > MAX_NAME ? trimmed.substring(0, MAX_NAME) : trimmed;
    }

    /** 是否周末（周六或周日）。 */
    public static boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    /** 无显式覆盖时的默认判定：周末休息、其余上班。 */
    public static boolean defaultWorkday(LocalDate date) {
        return !isWeekend(date);
    }
}
