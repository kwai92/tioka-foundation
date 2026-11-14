package com.foundation.starter.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.client.calendar.CalendarInfo;
import com.foundation.starter.client.calendar.HttpCalendarClient;
import com.foundation.starter.client.calendar.WorkdayCount;
import com.foundation.starter.client.calendar.WorkdayResult;
import com.foundation.starter.client.calendar.WorkdayShift;
import com.foundation.starter.client.http.CircuitBreaker;
import com.foundation.starter.client.http.FoundationHttpClient;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 工作日历客户端（F-09 §18.1）：路径与参数拼装、区域省略、日期解析。 */
class CalendarClientTest {

    private MockWebServer server;
    private HttpCalendarClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        FoundationHttpClient http = new FoundationHttpClient(new OkHttpClient(), new ObjectMapper(),
                baseUrl(), null, new CircuitBreaker(50, 20, 60_000, 30_000), 1, 1);
        client = new HttpCalendarClient(http);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void calendarsFiltersByRegionAndYear() throws Exception {
        server.enqueue(json("""
                [{"id":"cal_1","regionCode":"CN","year":2026,"name":"中国法定","source":"OFFICIAL","status":"ACTIVE"}]
                """));

        List<CalendarInfo> calendars = client.calendars("CN", 2026);

        assertThat(calendars).hasSize(1);
        assertThat(calendars.get(0).id()).isEqualTo("cal_1");
        assertThat(calendars.get(0).year()).isEqualTo(2026);
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/fdn/calendars?region=CN&year=2026");
    }

    @Test
    void workdayOmitsRegionWhenBlank() throws Exception {
        server.enqueue(json("""
                {"region":"CN","date":"2026-02-17","workday":false}
                """));

        WorkdayResult result = client.workday(null, LocalDate.of(2026, 2, 17));

        assertThat(result.region()).isEqualTo("CN");
        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 2, 17));
        assertThat(result.workday()).isFalse();
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/fdn/calendars/workday?date=2026-02-17");
    }

    @Test
    void addWorkdaysPassesFromAndDays() throws Exception {
        server.enqueue(json("""
                {"region":"CN","from":"2026-02-13","workdays":10,"result":"2026-03-04"}
                """));

        WorkdayShift shift = client.addWorkdays("CN", LocalDate.of(2026, 2, 13), 10);

        assertThat(shift.result()).isEqualTo(LocalDate.of(2026, 3, 4));
        assertThat(shift.workdays()).isEqualTo(10);
        assertThat(server.takeRequest().getPath())
                .isEqualTo("/api/fdn/calendars/add-workdays?region=CN&from=2026-02-13&days=10");
    }

    @Test
    void diffWorkdaysParsesRange() throws Exception {
        server.enqueue(json("""
                {"region":"CN","from":"2026-02-13","to":"2026-02-18","workdays":3}
                """));

        WorkdayCount count = client.diffWorkdays("CN", LocalDate.of(2026, 2, 13), LocalDate.of(2026, 2, 18));

        assertThat(count.workdays()).isEqualTo(3);
        assertThat(count.to()).isEqualTo(LocalDate.of(2026, 2, 18));
        assertThat(server.takeRequest().getPath())
                .isEqualTo("/api/fdn/calendars/diff-workdays?region=CN&from=2026-02-13&to=2026-02-18");
    }

    private String baseUrl() {
        String url = server.url("/").toString();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static MockResponse json(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
    }
}
