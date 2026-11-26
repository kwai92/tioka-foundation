package com.foundation.sync.spi;

import java.time.Instant;
import java.util.Collections;
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
        // 刻意不用 Map.copyOf：它**拒绝 null 值**（抛 NPE），而来源侧字段值天然可能为 null。
        // 实证路径：HttpFeishuApiClient.text() 对缺失/空白 JSON 字段返回 null，而
        // FeishuDepartment.toExternalRecord() 无条件 fields.put("parent_id", parentOpenDepartmentId)
        // ——飞书**根部门没有 parent_department_id**，故同步任何飞书组织树都会在根部门上抛 NPE。
        // 该异常沿 SyncEngine.execute 被吞成任务 FAILED（不打栈），表现为「飞书同步莫名失败」。
        // 与同一条流水线上的 MappedRecord / LandedRecord 保持一致（二者均容忍 null 值），
        // 且保持键集完整（不跳过 null 键），使 FieldMapper 与差异比对的「键缺失 vs 值为 null」语义不变。
        fields = fields == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
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
