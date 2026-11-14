package com.foundation.sync.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.sync.api.dto.SyncAction;
import com.foundation.sync.api.dto.SyncConflictType;
import com.foundation.sync.spi.SyncKey;
import com.foundation.sync.spi.SyncObjectType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 幂等落地（F-08 BAS-11 §7、§12）。
 *
 * <p>按幂等键 {@code SyncKey(来源, 外部ID)} 在 {@code (source, external_id)} 上 upsert，同一批 /
 * 重复批次零重复新增。非权威来源的落地范围被限制为「只写 {@code fdn_external_identity}」，
 * 越界写主数据记为 {@code AUTHORITY_CONFLICT}；被本地锁定字段跳过并记为 {@code LOCKED_FIELD}。
 */
public class IdempotentUpserter {

    /**
     * 落地一批差异条目。
     *
     * @param entries 待落地条目（引擎已剔除暂停的停用条目）
     * @param source  来源（权威判定与幂等键取源）
     * @param taskId  任务 ID（冲突与日志归属）
     * @param port    主数据落地端口
     */
    public UpsertOutcome land(List<DiffEntry> entries, SyncSource source, String taskId, MasterDataPort port) {
        int created = 0;
        int updated = 0;
        int disabled = 0;
        int moved = 0;
        int skipped = 0;
        List<LandedChange> changes = new ArrayList<>();
        List<SyncConflict> conflicts = new ArrayList<>();
        List<SyncLog> logs = new ArrayList<>();

        for (DiffEntry entry : entries == null ? List.<DiffEntry>of() : entries) {
            if (!source.authority() && entry.objectType() != SyncObjectType.EXTERNAL_IDENTITY) {
                conflicts.add(SyncConflict.of(taskId, source.id(), entry.objectType(), entry.externalId(),
                        SyncConflictType.AUTHORITY_CONFLICT,
                        Map.of("reason", "非权威来源不得写主数据", "objectType", entry.objectType().name())));
                logs.add(SyncLog.warn(taskId, entry.objectType(), entry.externalId(), SyncAction.SKIP,
                        "非权威来源越界，已跳过主数据落地"));
                skipped++;
                continue;
            }
            if (entry.hasLockedFields()) {
                conflicts.add(SyncConflict.of(taskId, source.id(), entry.objectType(), entry.externalId(),
                        SyncConflictType.LOCKED_FIELD,
                        Map.of("fields", new ArrayList<>(entry.lockedFields()))));
            }
            SyncKey key = new SyncKey(source.sourceType(), entry.externalId());
            try {
                switch (entry.kind()) {
                    case CREATE -> {
                        SyncKey parent = isBlank(entry.parentExternalId())
                                ? null : new SyncKey(source.sourceType(), entry.parentExternalId());
                        LandedRecord landed = port.create(entry.objectType(), key, entry.fields(), parent);
                        changes.add(new LandedChange(entry.objectType(), landed.localId(), landed.version(),
                                LandedChange.CREATED));
                        logs.add(SyncLog.info(taskId, entry.objectType(), entry.externalId(), SyncAction.CREATE,
                                "新增 " + entry.fields().keySet()));
                        created++;
                    }
                    case UPDATE -> {
                        Map<String, Object> write = writable(entry);
                        if (write.isEmpty()) {
                            logs.add(SyncLog.info(taskId, entry.objectType(), entry.externalId(), SyncAction.SKIP,
                                    "变更字段全部被本地锁定，跳过"));
                            skipped++;
                            break;
                        }
                        LandedRecord existing = require(port, entry.objectType(), key);
                        LandedRecord landed = port.update(entry.objectType(), existing, write);
                        changes.add(new LandedChange(entry.objectType(), landed.localId(), landed.version(),
                                LandedChange.UPDATED));
                        logs.add(SyncLog.info(taskId, entry.objectType(), entry.externalId(), SyncAction.UPDATE,
                                "更新 " + write.keySet()));
                        updated++;
                    }
                    case MOVE -> {
                        LandedRecord existing = require(port, entry.objectType(), key);
                        SyncKey newParent = isBlank(entry.parentExternalId())
                                ? null : new SyncKey(source.sourceType(), entry.parentExternalId());
                        LandedRecord landed = port.move(entry.objectType(), existing, newParent);
                        Map<String, Object> write = writable(entry);
                        if (!write.isEmpty()) {
                            landed = port.update(entry.objectType(), landed, write);
                        }
                        changes.add(new LandedChange(entry.objectType(), landed.localId(), landed.version(),
                                LandedChange.MOVED));
                        logs.add(SyncLog.info(taskId, entry.objectType(), entry.externalId(), SyncAction.MOVE,
                                "移动到 " + (entry.parentExternalId() == null ? "根" : entry.parentExternalId())));
                        moved++;
                    }
                    case DISABLE -> {
                        LandedRecord existing = require(port, entry.objectType(), key);
                        LandedRecord landed = port.disable(entry.objectType(), existing);
                        changes.add(new LandedChange(entry.objectType(), landed.localId(), landed.version(),
                                LandedChange.DISABLED));
                        logs.add(SyncLog.info(taskId, entry.objectType(), entry.externalId(), SyncAction.DISABLE,
                                "停用"));
                        disabled++;
                    }
                }
            } catch (BizException ex) {
                if (ex.getErrorCode() != ErrorCode.CONFLICT) {
                    throw ex;
                }
                conflicts.add(SyncConflict.of(taskId, source.id(), entry.objectType(), entry.externalId(),
                        SyncConflictType.DUPLICATE_KEY, Map.of("message", String.valueOf(ex.getMessage()))));
                logs.add(SyncLog.warn(taskId, entry.objectType(), entry.externalId(), SyncAction.ERROR,
                        ex.getMessage()));
                skipped++;
            }
        }
        return new UpsertOutcome(created, updated, disabled, moved, skipped, changes, conflicts, logs);
    }

    private Map<String, Object> writable(DiffEntry entry) {
        Map<String, Object> write = new LinkedHashMap<>();
        for (String field : entry.writableFields()) {
            write.put(field, entry.fields().get(field));
        }
        return write;
    }

    private LandedRecord require(MasterDataPort port, SyncObjectType objectType, SyncKey key) {
        return port.find(objectType, key)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "本地记录不存在，无法落地: " + key.externalId()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
