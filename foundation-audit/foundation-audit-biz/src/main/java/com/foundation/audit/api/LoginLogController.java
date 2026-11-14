package com.foundation.audit.api;

import com.foundation.starter.security.permission.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Instant;
import java.util.List;

/**
 * 登录日志 REST（F-09 BAS-10 §接口 {@code /api/fdn/login-logs}）：分页查询与失败统计。
 *
 * <p>本类不标 {@code @RestController}：由 {@code AuditAutoConfiguration} 以 {@code @Bean} 装配。
 */
@ResponseBody
@RequestMapping("/api/fdn/login-logs")
public class LoginLogController {

    private final LoginLogApi loginLogs;

    public LoginLogController(LoginLogApi loginLogs) {
        this.loginLogs = loginLogs;
    }

    /** 登录日志分页查询。 */
    @GetMapping
    @RequirePermission("fdn:audit:read")
    public LoginLogPageView page(@RequestParam(required = false) String loginName,
                                 @RequestParam(required = false) String personId,
                                 @RequestParam(required = false) Boolean success,
                                 @RequestParam(required = false) Instant from,
                                 @RequestParam(required = false) Instant to,
                                 @RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return loginLogs.page(new LoginLogQuery(loginName, personId, success, from, to, page, size));
    }

    /** 登录失败统计（按登录名聚合）。 */
    @GetMapping("/stats")
    @RequirePermission("fdn:audit:read")
    public List<LoginLogFailureStat> stats(@RequestParam(required = false) String loginName,
                                           @RequestParam(required = false) Instant from,
                                           @RequestParam(required = false) Instant to) {
        return loginLogs.failureStats(loginName, from, to);
    }
}
