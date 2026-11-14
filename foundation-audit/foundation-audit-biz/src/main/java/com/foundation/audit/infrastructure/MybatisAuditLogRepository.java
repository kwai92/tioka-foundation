package com.foundation.audit.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.audit.api.ActorType;
import com.foundation.audit.api.AuditOutcome;
import com.foundation.audit.api.AuditQuery;
import com.foundation.audit.api.RetentionClass;
import com.foundation.audit.domain.AuditLog;
import com.foundation.audit.infrastructure.mapper.AuditLogMapper;
import com.foundation.audit.infrastructure.persistence.AuditLogDO;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 审计行仓储的 MyBatis-Plus 实现（48-CS §4）；{@code details} 用 {@link ObjectMapper} 编解码。 */
public class MybatisAuditLogRepository implements AuditLogRepository {

    private final AuditLogMapper mapper;
    private final ObjectMapper json;

    public MybatisAuditLogRepository(AuditLogMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    public void save(AuditLog log) {
        mapper.insert(toDO(log));
    }

    @Override
    public List<AuditLog> page(Set<String> apps, AuditQuery query) {
        String[] cursor = parseCursor(query == null ? null : query.cursor());
        int size = query.size() <= 0 ? 20 : Math.min(query.size(), 100);
        Collection<String> effective = effectiveApps(apps, query);
        if (effective != null && effective.isEmpty()) {
            return List.of();
        }
        LocalDateTime cursorTs = cursor[0] == null ? null : LocalDateTime.ofInstant(
                Instant.ofEpochMilli(Long.parseLong(cursor[0])), ZoneOffset.UTC);
        return mapper.page(effective, blankToNull(query.actorId()), blankToNull(query.action()),
                        blankToNull(query.resourceType()), blankToNull(query.resourceId()),
                        blankToNull(query.outcome()), toUtc(query.from()), toUtc(query.to()),
                        cursorTs, cursor[1], size).stream().map(this::toDomain).toList();
    }

    @Override
    public List<AuditLog> listForExport(Set<String> apps, AuditQuery query, int limit) {
        Collection<String> effective = effectiveApps(apps, query);
        if (effective != null && effective.isEmpty()) {
            return List.of();
        }
        return mapper.listForExport(effective, blankToNull(query.actorId()), blankToNull(query.action()),
                        blankToNull(query.resourceType()), blankToNull(query.resourceId()),
                        blankToNull(query.outcome()), toUtc(query.from()), toUtc(query.to()),
                        limit).stream().map(this::toDomain).toList();
    }

    @Override
    public List<AuditLog> listByWindow(Instant from, Instant to) {
        return mapper.listByWindow(toUtc(from), toUtc(to)).stream().map(this::toDomain).toList();
    }

    /** 合并应用范围与查询应用过滤；null 表示不加应用条件，空集合表示交集为空。 */
    private static Collection<String> effectiveApps(Set<String> apps, AuditQuery query) {
        String filter = query == null ? null : blankToNull(query.app());
        if (apps == null) {
            return filter == null ? null : List.of(filter);
        }
        if (filter == null) {
            return apps;
        }
        return apps.contains(filter) ? List.of(filter) : List.of();
    }

    /** 解析游标 {@code {tsEpochMilli}|{id}}；非法抛 {@code BAD_REQUEST}。返回 {@code [tsMillis, id]}。 */
    private static String[] parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new String[]{null, null};
        }
        int idx = cursor.indexOf('|');
        if (idx <= 0 || idx >= cursor.length() - 1) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法游标");
        }
        String tsPart = cursor.substring(0, idx);
        try {
            Long.parseLong(tsPart);
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法游标");
        }
        return new String[]{tsPart, cursor.substring(idx + 1)};
    }

    private String writeDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("审计明细序列化失败", e);
        }
    }

    private Map<String, Object> readDetails(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return json.readValue(text, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("审计明细反序列化失败", e);
        }
    }

    private AuditLogDO toDO(AuditLog log) {
        AuditLogDO row = new AuditLogDO();
        row.setId(log.id());
        row.setAppCode(log.appCode());
        row.setAction(log.action());
        row.setResourceType(log.resourceType());
        row.setResourceId(log.resourceId());
        row.setOutcome(log.outcome() == null ? null : log.outcome().name());
        row.setReason(log.reason());
        row.setActorType(log.actorType() == null ? null : log.actorType().name());
        row.setActorId(log.actorId());
        row.setRunAs(log.runAs());
        row.setClientIp(log.clientIp());
        row.setUserAgent(log.userAgent());
        row.setOrigin(log.origin());
        row.setTraceId(log.traceId());
        row.setRequestId(log.requestId());
        row.setSubject(log.subject());
        row.setDataLabel(log.dataLabel());
        row.setRetentionClass(log.retentionClass() == null ? null : log.retentionClass().name());
        row.setDetails(writeDetails(log.details()));
        row.setTs(LocalDateTime.ofInstant(log.ts(), ZoneOffset.UTC));
        return row;
    }

    private AuditLog toDomain(AuditLogDO row) {
        return new AuditLog(row.getId(), row.getAppCode(), row.getAction(), row.getResourceType(),
                row.getResourceId(), AuditOutcome.of(row.getOutcome()), row.getReason(),
                ActorType.of(row.getActorType()), row.getActorId(), row.getRunAs(), row.getClientIp(),
                row.getUserAgent(), row.getOrigin(), row.getTraceId(), row.getRequestId(), row.getSubject(),
                row.getDataLabel(), RetentionClass.of(row.getRetentionClass()), readDetails(row.getDetails()),
                row.getTs().toInstant(ZoneOffset.UTC));
    }

    private static LocalDateTime toUtc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
