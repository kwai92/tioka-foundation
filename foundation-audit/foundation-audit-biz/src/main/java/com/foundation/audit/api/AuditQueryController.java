package com.foundation.audit.api;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.starter.security.permission.RequirePermission;
import com.foundation.starter.security.principal.Principal;
import com.foundation.starter.security.principal.PrincipalHolder;
import com.foundation.starter.security.snapshot.AuthzSnapshotClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 审计查询与导出 REST（F-09 BAS-10 §接口 {@code /api/fdn/audit/logs}）。
 *
 * <p>应用范围：具备 {@code fdn:audit:read:all} 时不限应用；否则必须指定 {@code app}，且只返回该应用的行。
 *
 * <p>本类不标 {@code @RestController}：由 {@code AuditAutoConfiguration} 以 {@code @Bean} 装配。
 */
@ResponseBody
@RequestMapping("/api/fdn/audit/logs")
public class AuditQueryController {

    /** 越应用读取所需的权限码。 */
    private static final String READ_ALL = "fdn:audit:read:all";

    private final AuditApi audit;
    private final AuthzSnapshotClient snapshotClient;

    public AuditQueryController(AuditApi audit, ObjectProvider<AuthzSnapshotClient> snapshotProvider) {
        this.audit = audit;
        this.snapshotClient = snapshotProvider.getIfAvailable();
    }

    /** 审计查询（游标分页）。 */
    @GetMapping
    @RequirePermission("fdn:audit:read")
    public AuditPageView query(@RequestParam(required = false) String app,
                               @RequestParam(required = false) String actorId,
                               @RequestParam(required = false) String action,
                               @RequestParam(required = false) String resourceType,
                               @RequestParam(required = false) String resourceId,
                               @RequestParam(required = false) String outcome,
                               @RequestParam(required = false) Instant from,
                               @RequestParam(required = false) Instant to,
                               @RequestParam(required = false) String cursor,
                               @RequestParam(defaultValue = "20") int size) {
        Set<String> scope = resolveScope(app);
        AuditQuery query = new AuditQuery(app, actorId, action, resourceType, resourceId, outcome, from, to,
                cursor, size);
        return audit.query(query, scope);
    }

    /** 导出（CSV，同条件取全量）；同时写一条 {@code audit.export} 审计。 */
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    @RequirePermission("fdn:audit:export")
    public String export(@RequestParam(required = false) String app,
                         @RequestParam(required = false) String actorId,
                         @RequestParam(required = false) String action,
                         @RequestParam(required = false) String resourceType,
                         @RequestParam(required = false) String resourceId,
                         @RequestParam(required = false) String outcome,
                         @RequestParam(required = false) Instant from,
                         @RequestParam(required = false) Instant to) {
        Set<String> scope = resolveScope(app);
        AuditQuery query = new AuditQuery(app, actorId, action, resourceType, resourceId, outcome, from, to,
                null, 0);
        String operatorId = PrincipalHolder.current().map(Principal::userId).orElse("system");
        return toCsv(audit.export(query, scope, operatorId));
    }

    /** 解析应用范围；无 all 权限且未指定应用时拒绝。 */
    private Set<String> resolveScope(String app) {
        String personId = PrincipalHolder.current().map(Principal::personId).orElse(null);
        boolean all = snapshotClient != null && personId != null
                && snapshotClient.load(personId).hasAll(READ_ALL);
        if (all) {
            return null;
        }
        if (app == null || app.isBlank()) {
            throw new BizException(ErrorCode.FORBIDDEN, "需指定应用或具备 fdn:audit:read:all");
        }
        return Set.of(app);
    }

    private static String toCsv(List<AuditView> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,appCode,action,resourceType,resourceId,outcome,actorType,actorId,traceId,requestId,"
                + "subject,retentionClass,ts\n");
        for (AuditView row : rows) {
            sb.append(csv(row.id())).append(',')
                    .append(csv(row.appCode())).append(',')
                    .append(csv(row.action())).append(',')
                    .append(csv(row.resourceType())).append(',')
                    .append(csv(row.resourceId())).append(',')
                    .append(csv(row.outcome())).append(',')
                    .append(csv(row.actorType())).append(',')
                    .append(csv(row.actorId())).append(',')
                    .append(csv(row.traceId())).append(',')
                    .append(csv(row.requestId())).append(',')
                    .append(csv(row.subject())).append(',')
                    .append(csv(row.retentionClass())).append(',')
                    .append(csv(row.ts() == null ? null : row.ts().toString())).append('\n');
        }
        return sb.toString();
    }

    /** CSV 字段转义：含逗号 / 引号 / 换行时用双引号包裹，内部 {@code "} 转义为 {@code ""}。 */
    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
