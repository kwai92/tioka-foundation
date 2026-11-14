package com.foundation.calendar.api;

import com.foundation.starter.audit.annotation.Audited;
import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * 工作日历 REST（F-09 BAS-18 §18.1 {@code /api/fdn/calendars}）：
 * 日历头列表、区间每日类型、维护某日类型、导入官方日历、单日判定与工作日推算。
 *
 * <p>读接口需登录；写接口需权限码 {@code fdn:calendar:admin}。
 * 本类不标 {@code @RestController}：由 {@code CalendarAutoConfiguration} 以 {@code @Bean} 装配。
 */
@ResponseBody
@RequestMapping("/api/fdn/calendars")
public class CalendarController {

    private static final String ADMIN = "fdn:calendar:admin";

    private final CalendarApi calendars;
    private final CalendarAdminApi admin;

    public CalendarController(CalendarApi calendars, CalendarAdminApi admin) {
        this.calendars = calendars;
        this.admin = admin;
    }

    /** 日历头列表（{@code region} / {@code year} 均可空）。 */
    @GetMapping
    public List<CalendarView> calendars(@RequestParam(required = false) String region,
                                        @RequestParam(required = false) Integer year) {
        return calendars.calendars(region, year);
    }

    /** 日期区间内的每日类型（含端点）。 */
    @GetMapping("/{region}/{year}/days")
    public List<CalendarDayView> days(@PathVariable String region,
                                      @PathVariable int year,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return calendars.days(region, year, from, to);
    }

    /** 维护某日类型（节假日 / 调休）。 */
    @PutMapping("/{region}/{year}/days")
    @RequirePermission(ADMIN)
    @Audited(action = "fdn.calendar.upsert-day", resource = "fdn_calendar")
    public CalendarDayView upsertDay(@PathVariable String region,
                                     @PathVariable int year,
                                     @RequestBody CalendarDayCmd cmd) {
        return admin.upsertDay(region, year, cmd, operator());
    }

    /** 导入官方日历（CSV：{@code yyyy-MM-dd,DAY_TYPE[,name]} 每行一天）。 */
    @PostMapping("/{region}/{year}/import")
    @RequirePermission(ADMIN)
    @Audited(action = "fdn.calendar.import", resource = "fdn_calendar")
    public CalendarImportResult importCalendar(@PathVariable String region,
                                               @PathVariable int year,
                                               @RequestParam("file") MultipartFile file) throws IOException {
        return admin.importCalendar(region, year, file.getBytes(), operator());
    }

    /** 单日是否工作日。 */
    @GetMapping("/workday")
    public WorkdayView workday(@RequestParam(required = false) String region,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return calendars.isWorkday(region, date);
    }

    /** {@code from} 起第 {@code days} 个工作日后的日期。 */
    @GetMapping("/add-workdays")
    public WorkdayShiftView addWorkdays(@RequestParam(required = false) String region,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam int days) {
        return calendars.addWorkdays(region, from, days);
    }

    /** 两个日期之间的工作日数（{@code [from, to)}）。 */
    @GetMapping("/diff-workdays")
    public WorkdayCountView diffWorkdays(@RequestParam(required = false) String region,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return calendars.diffWorkdays(region, from, to);
    }

    private static String operator() {
        return PrincipalHolder.current().map(Principal::userId).orElse("system");
    }
}
