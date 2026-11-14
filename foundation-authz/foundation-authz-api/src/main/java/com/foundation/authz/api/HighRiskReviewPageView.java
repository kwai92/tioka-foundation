package com.foundation.authz.api;

import java.util.List;

/**
 * 高风险授权复核分页视图（F-09 BAS-19 §接口）：游标格式 {@code <createAtEpochMilli>|<id>}。
 *
 * @param items      当前页（按申请时刻倒序）
 * @param nextCursor 下一页游标；无更多数据时为 {@code null}
 */
public record HighRiskReviewPageView(List<HighRiskReviewView> items, String nextCursor) {
}
