package com.foundation.audit.api;

import java.util.List;

/**
 * 登录日志分页视图（F-09 BAS-10 §登录日志）。
 *
 * @param items 本页行
 * @param total 总行数
 * @param page  页码（从 1 起）
 * @param size  每页行数
 */
public record LoginLogPageView(List<LoginLogView> items, long total, int page, int size) {
}
