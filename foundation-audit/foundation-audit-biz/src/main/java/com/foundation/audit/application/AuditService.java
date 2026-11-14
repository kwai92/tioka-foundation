package com.foundation.audit.application;

import com.foundation.audit.api.AuditApi;
import com.foundation.audit.api.AuditPageView;
import com.foundation.audit.api.AuditQuery;
import com.foundation.audit.api.AuditReportItem;
import com.foundation.audit.api.AuditView;
import com.foundation.audit.api.RetentionClass;
import com.foundation.audit.config.AuditProperties;
import com.foundation.audit.domain.AuditLog;
import com.foundation.audit.domain.AuditSeal;
import com.foundation.audit.infrastructure.AuditLogRepository;
import com.foundation.audit.infrastructure.AuditReportReceiptRepository;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 审计应用服务（F-09 BAS-10 §上报 / 查询 / 导出）：上报幂等、游标查询与导出审计。
 *
 * <p>写方法声明事务边界；忽略 {@code appCode} 等由上报条目自带。查询与导出严格受应用范围约束。
 */
public class AuditService implements AuditApi {

    private final AuditLogRepository auditLogs;
    private final AuditReportReceiptRepository receipts;
    private final AuditProperties properties;

    public AuditService(AuditLogRepository auditLogs, AuditReportReceiptRepository receipts,
                        AuditProperties properties) {
        this.auditLogs = auditLogs;
        this.receipts = receipts;
        this.properties = properties;
    }

    @Override
    @Transactional
    public int report(String idempotencyKey, List<AuditReportItem> events) {
        if (events == null || events.isEmpty()) {
            return 0;
        }
        if (events.size() > properties.getReportMaxBatch()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "单批上报条数超过上限");
        }
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey;
        if (key != null) {
            Optional<Integer> accepted = receipts.findAccepted(key);
            if (accepted.isPresent()) {
                return accepted.get();
            }
        }
        for (AuditReportItem item : events) {
            auditLogs.save(AuditLog.fromReport(item));
        }
        int accepted = events.size();
        if (key != null) {
            String appCode = events.get(0) == null ? null : events.get(0).appCode();
            receipts.save(key, appCode, accepted);
        }
        return accepted;
    }

    @Override
    public AuditPageView query(AuditQuery query, Set<String> appScope) {
        int size = query.size() <= 0 ? 20 : Math.min(query.size(), 100);
        List<AuditLog> rows = auditLogs.page(appScope, query);
        String nextCursor = null;
        if (rows.size() == size && !rows.isEmpty()) {
            AuditLog last = rows.get(rows.size() - 1);
            nextCursor = last.ts().toEpochMilli() + "|" + last.id();
        }
        List<AuditView> items = rows.stream().map(AuditService::toView).toList();
        return new AuditPageView(items, nextCursor);
    }

    @Override
    @Transactional
    public List<AuditView> export(AuditQuery query, Set<String> appScope, String operatorId) {
        int limit = properties.getReportMaxBatch() * 50;
        List<AuditLog> rows = auditLogs.listForExport(appScope, query, limit);

        Map<String, Object> details = new LinkedHashMap<>();
        putIfPresent(details, "app", query.app());
        putIfPresent(details, "actorId", query.actorId());
        putIfPresent(details, "action", query.action());
        putIfPresent(details, "outcome", query.outcome());
        putIfPresent(details, "from", query.from());
        putIfPresent(details, "to", query.to());
        details.put("rows", rows.size());
        details.put("fileHash", AuditSeal.chainHash(null, rows));

        auditLogs.save(AuditLog.self("fdn", "audit.export", operatorId, conditionSummary(query),
                RetentionClass.OPS, details));
        return rows.stream().map(AuditService::toView).toList();
    }

    private static String conditionSummary(AuditQuery query) {
        return "app=" + query.app() + ";actorId=" + query.actorId() + ";action=" + query.action()
                + ";outcome=" + query.outcome();
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static AuditView toView(AuditLog log) {
        long durationMillis = 0L;
        String detail = null;
        if (log.details() != null) {
            if (log.details().get("durationMillis") instanceof Number number) {
                durationMillis = number.longValue();
            }
            Object raw = log.details().get("detail");
            detail = raw == null ? null : String.valueOf(raw);
        }
        return new AuditView(log.id(), log.appCode(), log.action(), log.resourceType(), log.resourceId(),
                log.outcome() == null ? null : log.outcome().name(), log.reason(),
                log.actorType() == null ? null : log.actorType().name(), log.actorId(), log.runAs(),
                log.clientIp(), log.userAgent(), log.origin(), log.traceId(), log.requestId(), log.subject(),
                log.dataLabel(), log.retentionClass() == null ? null : log.retentionClass().name(),
                detail, durationMillis, log.ts());
    }
}
