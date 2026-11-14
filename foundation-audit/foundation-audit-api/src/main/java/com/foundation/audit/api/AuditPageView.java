package com.foundation.audit.api;

import java.util.List;

/**
 * 审计分页视图（F-09 BAS-10 §查询）——游标分页。
 *
 * @param items      本页行
 * @param nextCursor 下一页游标；无更多行时为 {@code null}
 */
public record AuditPageView(List<AuditView> items, String nextCursor) {
}
