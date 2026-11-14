package com.foundation.sync.domain;

import com.foundation.sync.api.dto.DiffKind;
import com.foundation.sync.spi.SyncObjectType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 差异条目（F-08 BAS-11 §6）：新增、修改、停用、移动之一。
 *
 * <p>一条来源记录最多产出一条差异条目；同时发生字段变更与换上级时归类为 {@link DiffKind#MOVE}，
 * 字段变更一并携带，避免重复落地。
 *
 * @param objectType       对象类型
 * @param externalId       来源侧标识
 * @param kind             差异类型
 * @param fields           目标内部字段全量取值（落地时取 {@code changedFields} 子集）
 * @param changedFields    相对本地的变更字段（新建时为全部字段）
 * @param lockedFields     被本地锁定而跳过的变更字段
 * @param parentExternalId 上级对象外部 ID（组织单元），可空
 */
public record DiffEntry(SyncObjectType objectType,
                        String externalId,
                        DiffKind kind,
                        Map<String, Object> fields,
                        Set<String> changedFields,
                        Set<String> lockedFields,
                        String parentExternalId) {

    public DiffEntry {
        Map<String, Object> fieldCopy = new LinkedHashMap<>();
        if (fields != null) {
            fieldCopy.putAll(fields);
        }
        fields = Collections.unmodifiableMap(fieldCopy);
        changedFields = changedFields == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(changedFields));
        lockedFields = lockedFields == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(lockedFields));
    }

    /** 可落地的变更字段（变更字段减去本地锁定字段）。 */
    public Set<String> writableFields() {
        Set<String> writable = new LinkedHashSet<>(changedFields);
        writable.removeAll(lockedFields);
        return writable;
    }

    /** 是否被本地锁定挡下了部分字段。 */
    public boolean hasLockedFields() {
        return !lockedFields.isEmpty();
    }
}
