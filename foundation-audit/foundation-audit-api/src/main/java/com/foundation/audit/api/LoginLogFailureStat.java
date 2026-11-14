package com.foundation.audit.api;

/**
 * 登录失败统计（F-09 BAS-10 §登录日志）——按登录名聚合失败次数。
 *
 * @param loginName 登录名
 * @param failures  失败次数
 */
public record LoginLogFailureStat(String loginName, long failures) {
}
