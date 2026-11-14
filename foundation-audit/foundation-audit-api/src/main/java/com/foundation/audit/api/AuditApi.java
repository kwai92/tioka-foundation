package com.foundation.audit.api;

import java.util.List;
import java.util.Set;

/**
 * 审计契约（F-09 BAS-10 §接口）：批量上报、查询与导出。
 *
 * <p>上报要求权限码 {@code fdn:audit:report}；查询 / 导出要求 {@code fdn:audit:read} /
 * {@code fdn:audit:export}，越应用范围读取需 {@code fdn:audit:read:all}。
 */
public interface AuditApi {

    /** 批量上报审计（幂等：同 idempotencyKey 重复上报不重复入库）；返回接收条数。 */
    int report(String idempotencyKey, List<AuditReportItem> events);

    /** 审计查询；appScope 为 null 表示不限应用（对应 fdn:audit:read:all），否则只返回 app_code 命中集合的行。 */
    AuditPageView query(AuditQuery query, Set<String> appScope);

    /** 导出（同条件取全量，上限 report.max-batch 的 50 倍）；同时写一条 audit.export 审计。 */
    List<AuditView> export(AuditQuery query, Set<String> appScope, String operatorId);
}
