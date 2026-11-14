package com.foundation.sync.spi;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 来源侧一条记录（F-08 BAS-11 §2）。
 *
 * <p>字段值为来源原始取值（未做内部字段映射）；映射与转换由 {@code FieldMapper} 依
 * {@code fdn_sync_field_mapping} 完成。
 *
 * @param objectType      对象类型
 * @param externalId      来源侧标识（幂等键的一半）
 * @param fields          外部字段名 → 原始取值
 * @param parentExternalId 上级对象的外部 ID（组织单元移动判定），可空
 * @param deleted         来源侧是否已删除（引擎据此算「停用」差异）
 * @param updatedAt       来源侧最后更新时间，可空
 */
public record ExternalRecord(SyncObjectType objectType,
                             String externalId,
                             Map<String, Object> fields,
                             String parentExternalId,
                             boolean deleted,
                             Instant updatedAt) {

    public ExternalRecord {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    /** 便捷构造：未删除、无上级、无更新时间。 */
    public static ExternalRecord of(SyncObjectType objectType, String externalId, Map<String, Object> fields) {
        return new ExternalRecord(objectType, externalId, fields, null, false, null);
    }

    /** 取该记录的幂等键。 */
    public SyncKey key(String sourceType) {
        return SyncKey.of(sourceType, externalId);
    }

    /** 替换字段（映射后回填用），其余属性不变。 */
    public ExternalRecord withFields(Map<String, Object> mappedFields) {
        return new ExternalRecord(objectType, externalId, mappedFields, parentExternalId, deleted, updatedAt);
    }

    /** 以有序 Map 覆盖式放入一个字段。 */
    public Map<String, Object> fieldsMutable() {
        return new LinkedHashMap<>(fields);
    }
}
