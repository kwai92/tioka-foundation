package com.foundation.audit.api;

import java.time.Instant;
import java.util.List;

/**
 * 登录日志契约（F-09 BAS-10 §接口）：从 Keycloak 事件采集、分页查询与失败统计。
 *
 * <p>查询要求权限码 {@code fdn:audit:read}。
 */
public interface LoginLogApi {

    /** 从 Keycloak 事件采集登录日志（event_id 幂等）；返回新增条数。 */
    int collect();

    /** 登录日志分页查询。 */
    LoginLogPageView page(LoginLogQuery query);

    /** 登录失败统计（按登录名聚合）。 */
    List<LoginLogFailureStat> failureStats(String loginName, Instant from, Instant to);
}
