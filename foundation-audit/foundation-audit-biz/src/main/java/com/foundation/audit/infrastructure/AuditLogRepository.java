package com.foundation.audit.infrastructure;

import com.foundation.audit.api.AuditQuery;
import com.foundation.audit.domain.AuditLog;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 审计行仓储（F-09 BAS-10，MyBatis-Plus）：追加、条件分页与导出、封印窗口扫描。 */
public interface AuditLogRepository {

    /** 追加一行审计（append-only）。 */
    void save(AuditLog log);

    /** 条件游标分页；{@code apps} 为应用范围（null 表示不限），{@code query.app} 为额外应用过滤。 */
    List<AuditLog> page(Set<String> apps, AuditQuery query);

    /** 导出：同条件取全量，以 {@code limit} 控制上限。 */
    List<AuditLog> listForExport(Set<String> apps, AuditQuery query, int limit);

    /** 封印窗口扫描：取 {@code [from, to)} 内行并按 id 升序。 */
    List<AuditLog> listByWindow(Instant from, Instant to);
}
