package com.foundation.calendar.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 官方日历导入解析（F-09 BAS-18 §18.1，{@code POST /api/fdn/calendars/{region}/{year}/import}）。
 *
 * <p>每行一天：{@code yyyy-MM-dd,DAY_TYPE[,name]}；空行与 {@code #} 开头为注释。
 */
public final class CalendarImportParser {

    private CalendarImportParser() {
    }

    /** 一行解析结果。 */
    public record ParsedDay(LocalDate date, DayType dayType, String name) {
    }

    /** 解析 CSV 文本；格式错误抛参数错误（带行号）。 */
    public static List<ParsedDay> parse(byte[] content) {
        String text = content == null ? "" : new String(content, StandardCharsets.UTF_8);
        List<ParsedDay> days = new ArrayList<>();
        int lineNo = 0;
        for (String raw : text.split("\r?\n")) {
            lineNo++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split(",", 3);
            if (parts.length < 2) {
                throw new BizException(ErrorCode.BAD_REQUEST, "第 " + lineNo + " 行格式应为「日期,类型[,名称]」");
            }
            LocalDate date;
            try {
                date = LocalDate.parse(parts[0].trim());
            } catch (DateTimeParseException ex) {
                throw new BizException(ErrorCode.BAD_REQUEST, "第 " + lineNo + " 行日期不合法：" + parts[0].trim());
            }
            DayType type = DayType.of(parts[1]);
            String name = parts.length == 3 ? CalendarRules.normalizeName(parts[2]) : null;
            days.add(new ParsedDay(date, type, name));
        }
        return days;
    }
}
