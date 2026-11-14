package com.foundation.audit.infrastructure;

import com.foundation.audit.api.LoginLogFailureStat;
import com.foundation.audit.api.LoginLogQuery;
import com.foundation.audit.domain.LoginLog;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 登录日志仓储（F-09 BAS-10，MyBatis-Plus）：幂等采集、分页、计数、失败统计与水位读取。 */
public interface LoginLogRepository {

    /** 按 {@code event_id} 幂等插入；已存在返回 {@code false}，插入成功返回 {@code true}。 */
    boolean saveIfAbsent(LoginLog log);

    /** 分页查询。 */
    List<LoginLog> page(LoginLogQuery query);

    /** 同条件计数。 */
    long count(LoginLogQuery query);

    /** 失败统计（按登录名聚合）。 */
    List<LoginLogFailureStat> failureStats(String loginName, Instant from, Instant to);

    /** 最近一条日志时刻（采集水位）；无数据返回空。 */
    Optional<Instant> latestTs();
}
