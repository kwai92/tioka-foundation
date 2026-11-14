package com.foundation.sync.domain;

import com.foundation.sync.api.dto.DiffKind;
import com.foundation.sync.spi.SyncKey;
import com.foundation.sync.spi.SyncObjectType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 差异算法（F-08 BAS-11 §6、§12）：算新增 / 修改 / 停用 / 移动。
 *
 * <p>预演（dry-run）与落地共用本算法，避免口径不一致。差异条目按对象类型逐个算出后累加。
 */
public class DiffPreviewService {

    private final LocalLockResolver lockResolver;

    public DiffPreviewService(LocalLockResolver lockResolver) {
        this.lockResolver = lockResolver;
    }

    /**
     * 计算某对象类型一批来源记录的差异。
     *
     * @param objectType   对象类型
     * @param records      来源记录（已映射为内部字段）
     * @param sourceType   来源类型（组成幂等键）
     * @param port         主数据落地端口（读本地快照）
     * @param fullSnapshot 是否全量快照（据此算出「已消失对象」的停用差异）
     */
    public DiffResult preview(SyncObjectType objectType,
                              List<MappedRecord> records,
                              String sourceType,
                              MasterDataPort port,
                              boolean fullSnapshot) {
        List<DiffEntry> entries = new ArrayList<>();
        Set<String> present = new HashSet<>();
        List<MappedRecord> batch = records == null ? List.of() : records;
        for (MappedRecord record : batch) {
            present.add(record.externalId());
            SyncKey key = new SyncKey(sourceType, record.externalId());
            Optional<LandedRecord> existingOpt = port.find(objectType, key);
            if (record.deleted()) {
                existingOpt.filter(existing -> !existing.disabled())
                        .ifPresent(existing -> entries.add(disable(objectType, record.externalId(), existing)));
                continue;
            }
            if (existingOpt.isEmpty()) {
                entries.add(create(objectType, record));
                continue;
            }
            LandedRecord existing = existingOpt.get();
            Set<String> changed = changedFields(existing, record);
            if (isMoved(objectType, existing, record)) {
                entries.add(move(objectType, record, existing, changed, port));
            } else if (!changed.isEmpty()) {
                entries.add(update(objectType, record, existing, changed, port));
            }
        }
        if (fullSnapshot) {
            for (LandedRecord existing : port.listBySource(objectType, sourceType)) {
                if (existing.disabled() || present.contains(existing.key().externalId())) {
                    continue;
                }
                entries.add(disable(objectType, existing.key().externalId(), existing));
            }
        }
        return DiffResult.of(entries);
    }

    private DiffEntry create(SyncObjectType objectType, MappedRecord record) {
        return new DiffEntry(objectType, record.externalId(), DiffKind.CREATE,
                record.fields(), new LinkedHashSet<>(record.fields().keySet()), Set.of(),
                record.parentExternalId());
    }

    private DiffEntry update(SyncObjectType objectType, MappedRecord record, LandedRecord existing,
                             Set<String> changed, MasterDataPort port) {
        Set<String> locked = lockResolver.lockedAmong(changed,
                port.lockedFields(objectType, existing.localId()));
        return new DiffEntry(objectType, record.externalId(), DiffKind.UPDATE,
                record.fields(), changed, locked, record.parentExternalId());
    }

    private DiffEntry move(SyncObjectType objectType, MappedRecord record, LandedRecord existing,
                           Set<String> changed, MasterDataPort port) {
        Set<String> locked = lockResolver.lockedAmong(changed,
                port.lockedFields(objectType, existing.localId()));
        return new DiffEntry(objectType, record.externalId(), DiffKind.MOVE,
                record.fields(), changed, locked, record.parentExternalId());
    }

    private DiffEntry disable(SyncObjectType objectType, String externalId, LandedRecord existing) {
        return new DiffEntry(objectType, externalId, DiffKind.DISABLE,
                Map.of(), Set.of(), Set.of(), existing.parentExternalId());
    }

    private Set<String> changedFields(LandedRecord existing, MappedRecord record) {
        Set<String> changed = new LinkedHashSet<>();
        for (Map.Entry<String, Object> entry : record.fields().entrySet()) {
            if (!sameValue(existing.fields().get(entry.getKey()), entry.getValue())) {
                changed.add(entry.getKey());
            }
        }
        return changed;
    }

    private boolean isMoved(SyncObjectType objectType, LandedRecord existing, MappedRecord record) {
        if (objectType != SyncObjectType.ORG_UNIT) {
            return false;
        }
        return !Objects.equals(blankToNull(existing.parentExternalId()), blankToNull(record.parentExternalId()));
    }

    private static boolean sameValue(Object left, Object right) {
        if (Objects.equals(left, right)) {
            return true;
        }
        return Objects.equals(normalize(left), normalize(right));
    }

    private static String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
