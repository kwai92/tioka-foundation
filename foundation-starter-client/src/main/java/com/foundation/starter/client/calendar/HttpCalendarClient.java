package com.foundation.starter.client.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.foundation.starter.client.http.FoundationHttpClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作日历的 HTTP 实现（F-09 BAS-18 §18.1 {@code /api/fdn/calendars}）。
 *
 * <p>只读接口，由 {@code FoundationHttpClient} 统一承载认证、重试与 trace 透传。
 */
public class HttpCalendarClient implements CalendarClient {

    private final FoundationHttpClient http;

    public HttpCalendarClient(FoundationHttpClient http) {
        this.http = http;
    }

    @Override
    public List<CalendarInfo> calendars(String region, Integer year) {
        StringBuilder path = new StringBuilder("/api/fdn/calendars");
        char sep = '?';
        if (region != null && !region.isBlank()) {
            path.append(sep).append("region=").append(encode(region));
            sep = '&';
        }
        if (year != null) {
            path.append(sep).append("year=").append(year);
        }
        JsonNode node = http.get(path.toString());
        if (node == null) {
            return List.of();
        }
        JsonNode array = node.isArray() ? node : node.path("items");
        List<CalendarInfo> list = new ArrayList<>();
        array.forEach(item -> list.add(new CalendarInfo(
                item.path("id").asText(null),
                item.path("regionCode").asText(null),
                item.path("year").asInt(0),
                item.path("name").asText(null),
                item.path("source").asText(null),
                item.path("status").asText(null))));
        return list;
    }

    @Override
    public WorkdayResult workday(String region, LocalDate date) {
        JsonNode node = http.get("/api/fdn/calendars/workday"
                + query(regionParam(region), "date=" + date));
        if (node == null || node.isNull()) {
            return null;
        }
        return new WorkdayResult(
                node.path("region").asText(null),
                parseDate(node.path("date").asText(null)),
                node.path("workday").asBoolean(false));
    }

    @Override
    public WorkdayShift addWorkdays(String region, LocalDate from, int days) {
        JsonNode node = http.get("/api/fdn/calendars/add-workdays"
                + query(regionParam(region), "from=" + from, "days=" + days));
        if (node == null || node.isNull()) {
            return null;
        }
        return new WorkdayShift(
                node.path("region").asText(null),
                parseDate(node.path("from").asText(null)),
                node.path("workdays").asInt(0),
                parseDate(node.path("result").asText(null)));
    }

    @Override
    public WorkdayCount diffWorkdays(String region, LocalDate from, LocalDate to) {
        JsonNode node = http.get("/api/fdn/calendars/diff-workdays"
                + query(regionParam(region), "from=" + from, "to=" + to));
        if (node == null || node.isNull()) {
            return null;
        }
        return new WorkdayCount(
                node.path("region").asText(null),
                parseDate(node.path("from").asText(null)),
                parseDate(node.path("to").asText(null)),
                node.path("workdays").asInt(0));
    }

    /** {@code region} 为空时省略该参数（由服务端按默认区域回退）。 */
    private static String regionParam(String region) {
        return region == null || region.isBlank() ? null : "region=" + encode(region);
    }

    /** 拼接查询串，跳过为空的参数。 */
    private static String query(String... params) {
        StringBuilder query = new StringBuilder();
        for (String param : params) {
            if (param == null || param.isBlank()) {
                continue;
            }
            query.append(query.isEmpty() ? '?' : '&').append(param);
        }
        return query.toString();
    }

    private static LocalDate parseDate(String value) {
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
