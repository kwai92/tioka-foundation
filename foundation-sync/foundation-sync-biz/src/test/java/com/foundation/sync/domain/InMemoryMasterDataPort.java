package com.foundation.sync.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.sync.spi.SyncKey;
import com.foundation.sync.spi.SyncObjectType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link MasterDataPort} 的内存实现（单元测试用，不启动 Spring 容器）。
 *
 * <p>以 {@code (objectType, 来源, 外部 ID)} 为索引模拟 upsert；可通过 {@link #failCreateFor(String)}
 * 让指定外部 ID 的首建抛 {@code CONFLICT}，以验证 {@link IdempotentUpserter} 的唯一键冲突分支。
 */
final class InMemoryMasterDataPort implements MasterDataPort {

    private final Map<String, Entry> byIndex = new LinkedHashMap<>();
    private final Set<String> conflictingExternalIds = new HashSet<>();
    private final AtomicInteger sequence = new AtomicInteger();

    /** 一条本地记录及其锁定字段。 */
    private record Entry(SyncObjectType objectType, LandedRecord record, Set<String> lockedFields) {
    }

    private static String index(SyncObjectType objectType, SyncKey key) {
        return objectType.name() + "|" + key.sourceType() + "|" + key.externalId();
    }

    /** 让指定外部 ID 的首建抛唯一键冲突（模拟 {@code (source, external_id)} 唯一约束）。 */
    void failCreateFor(String externalId) {
        conflictingExternalIds.add(externalId);
    }

    @Override
    public Optional<LandedRecord> find(SyncObjectType objectType, SyncKey key) {
        return Optional.ofNullable(byIndex.get(index(objectType, key))).map(Entry::record);
    }

    @Override
    public List<LandedRecord> listBySource(SyncObjectType objectType, String sourceType) {
        List<LandedRecord> records = new ArrayList<>();
        for (Entry entry : byIndex.values()) {
            if (entry.objectType() == objectType && entry.record().key().sourceType().equals(sourceType)) {
                records.add(entry.record());
            }
        }
        return records;
    }

    @Override
    public LandedRecord create(SyncObjectType objectType, SyncKey key, Map<String, Object> fields, SyncKey parentKey) {
        if (conflictingExternalIds.contains(key.externalId())) {
            throw new BizException(ErrorCode.CONFLICT, "唯一键冲突: " + key.externalId());
        }
        LandedRecord record = new LandedRecord("loc_" + sequence.incrementAndGet(), key, 1,
                fields == null ? Map.of() : new LinkedHashMap<>(fields), false,
                parentKey == null ? null : parentKey.externalId());
        byIndex.put(index(objectType, key), new Entry(objectType, record, Set.of()));
        return record;
    }

    @Override
    public LandedRecord update(SyncObjectType objectType, LandedRecord existing, Map<String, Object> fields) {
        Map<String, Object> merged = new LinkedHashMap<>(existing.fields());
        if (fields != null) {
            merged.putAll(fields);
        }
        LandedRecord record = new LandedRecord(existing.localId(), existing.key(), existing.version() + 1,
                merged, existing.disabled(), existing.parentExternalId());
        replace(objectType, record);
        return record;
    }

    @Override
    public LandedRecord disable(SyncObjectType objectType, LandedRecord existing) {
        LandedRecord record = new LandedRecord(existing.localId(), existing.key(), existing.version() + 1,
                existing.fields(), true, existing.parentExternalId());
        replace(objectType, record);
        return record;
    }

    @Override
    public LandedRecord move(SyncObjectType objectType, LandedRecord existing, SyncKey newParentKey) {
        LandedRecord record = new LandedRecord(existing.localId(), existing.key(), existing.version() + 1,
                existing.fields(), existing.disabled(),
                newParentKey == null ? null : newParentKey.externalId());
        replace(objectType, record);
        return record;
    }

    @Override
    public Set<String> lockedFields(SyncObjectType objectType, String localId) {
        for (Entry entry : byIndex.values()) {
            if (entry.objectType() == objectType && entry.record().localId().equals(localId)) {
                return entry.lockedFields();
            }
        }
        return Set.of();
    }

    @Override
    public void setLockedFields(SyncObjectType objectType, String localId, Set<String> fields) {
        for (Map.Entry<String, Entry> mapEntry : byIndex.entrySet()) {
            Entry entry = mapEntry.getValue();
            if (entry.objectType() == objectType && entry.record().localId().equals(localId)) {
                mapEntry.setValue(new Entry(objectType, entry.record(),
                        fields == null ? Set.of() : new LinkedHashSet<>(fields)));
                return;
            }
        }
    }

    private void replace(SyncObjectType objectType, LandedRecord record) {
        Set<String> locked = lockedFields(objectType, record.localId());
        byIndex.put(index(objectType, record.key()), new Entry(objectType, record, locked));
    }
}
