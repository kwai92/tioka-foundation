package com.foundation.sync.domain;

import com.foundation.sync.spi.SyncKey;
import com.foundation.sync.spi.SyncObjectType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 已映射为内部字段的来源记录（F-08 BAS-11 §4、§7）。
 *
 * <p>由 {@link FieldMapper} 把外部字段翻译成内部字段后的结果；差异算法只认内部字段口径。
 *
 * @param objectType       对象类型
 * @param externalId       来源侧标识
 * @param fields           内部字段取值（已应用默认值与转换）
 * @param parentExternalId 上级对象外部 ID（组织单元），可空
 * @param deleted          来源侧是否标记删除
 */
public record MappedRecord(SyncObjectType objectType,
                           String externalId,
                           Map<String, Object> fields,
                           String parentExternalId,
                           boolean deleted) {

    public MappedRecord {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (fields != null) {
            copy.putAll(fields);
        }
        fields = Collections.unmodifiableMap(copy);
    }

    /** 幂等键（来源 + 外部 ID）。 */
    public SyncKey key(String sourceType) {
        return new SyncKey(sourceType, externalId);
    }
}
