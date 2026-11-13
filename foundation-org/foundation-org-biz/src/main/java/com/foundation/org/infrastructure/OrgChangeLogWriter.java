package com.foundation.org.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.common.id.Ids;
import com.foundation.org.api.dto.ChangeAction;
import com.foundation.org.api.dto.ChangeEntityType;
import com.foundation.org.domain.OrgChangeLogEntry;
import com.foundation.org.domain.OrgIdPrefix;
import com.foundation.org.infrastructure.mapper.OrgChangeLogMapper;
import com.foundation.org.infrastructure.persistence.OrgChangeLogDO;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * 变更历史写入与查询（F-03 §4.3、§7.3）：追加 {@code fdn_org_change_log}，只追加不修改。
 *
 * <p>须由调用方置于业务事务内，保证「业务落库」与「变更历史」同生共死。
 */
public class OrgChangeLogWriter {

    /** 无请求上下文时的兜底操作人。 */
    public static final String SYSTEM_ACTOR = "system";

    private final OrgChangeLogMapper mapper;
    private final ObjectMapper json;

    public OrgChangeLogWriter(OrgChangeLogMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    /**
     * 追加一条变更历史。
     *
     * @param before 变更前字段白名单摘要，可空
     * @param after  变更后字段白名单摘要，可空
     */
    public void append(ChangeEntityType entityType, String entityId, ChangeAction action,
                       Map<String, Object> before, Map<String, Object> after,
                       String source, String actor) {
        OrgChangeLogDO row = new OrgChangeLogDO();
        row.setId(Ids.newId(OrgIdPrefix.CHANGE_LOG));
        row.setEntityType(entityType.name());
        row.setEntityId(entityId);
        row.setAction(action.name());
        row.setBeforeJson(writeJson(before));
        row.setAfterJson(writeJson(after));
        row.setSource(source);
        row.setActor(actor == null || actor.isBlank() ? SYSTEM_ACTOR : actor);
        row.setCreateAt(LocalDateTime.now(ZoneOffset.UTC));
        mapper.insert(row);
    }

    /** 查询某对象的历次变更，按发生时间倒序。 */
    public List<OrgChangeLogEntry> findChanges(ChangeEntityType entityType, String entityId, int limit) {
        return mapper.selectList(Wrappers.<OrgChangeLogDO>lambdaQuery()
                        .eq(OrgChangeLogDO::getEntityType, entityType.name())
                        .eq(OrgChangeLogDO::getEntityId, entityId)
                        .orderByDesc(OrgChangeLogDO::getCreateAt)
                        .last("LIMIT " + Math.max(limit, 1)))
                .stream().map(OrgChangeLogWriter::toDomain).toList();
    }

    private String writeJson(Map<String, Object> summary) {
        if (summary == null || summary.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(summary);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("变更历史摘要序列化失败", ex);
        }
    }

    private static OrgChangeLogEntry toDomain(OrgChangeLogDO row) {
        return new OrgChangeLogEntry(row.getId(), ChangeEntityType.valueOf(row.getEntityType()),
                row.getEntityId(), ChangeAction.valueOf(row.getAction()), row.getBeforeJson(),
                row.getAfterJson(), row.getSource(), row.getActor(),
                row.getCreateAt() == null ? null : row.getCreateAt().toInstant(ZoneOffset.UTC));
    }
}
