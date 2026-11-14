package com.foundation.audit.api;

import java.time.Instant;

/**
 * 登录日志查询条件（F-09 BAS-10 §登录日志）。
 *
 * @param loginName 登录名，可空
 * @param personId  人员 ID，可空
 * @param success   是否成功，可空
 * @param from      起始时刻（含），可空
 * @param to        结束时刻（不含），可空
 * @param page      页码（从 1 起；{@code <= 0} 取默认 1）
 * @param size      每页行数（{@code <= 0} 取默认 20，上限 100）
 */
public record LoginLogQuery(String loginName, String personId, Boolean success, Instant from, Instant to,
                            int page, int size) {
}
